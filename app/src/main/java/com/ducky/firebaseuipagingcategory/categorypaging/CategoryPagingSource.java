package com.ducky.firebaseuipagingcategory.categorypaging;

import android.annotation.SuppressLint;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class CategoryPagingSource extends RxPagingSource<Pair<Double, String>, DataSnapshot> {
    public static String TAG = CategoryPagingSource.class.getSimpleName();
    private final Query mQuery;
    private final Double mCateID;
    private final String mCategoryKey;
    private final Boolean mIsAscending;

    private static final String STATUS_DATABASE_NOT_FOUND = "DATA_NOT_FOUND";
    private static final String MESSAGE_DATABASE_NOT_FOUND = "Data not found at given child path!";
    private static final String DETAILS_DATABASE_NOT_FOUND = "No data was returned for the given query: ";

    public CategoryPagingSource(Query query, Double categoryNum, String categoryKey, Boolean isAscending) {
        this.mQuery = query;
        this.mCategoryKey = categoryKey;
        this.mCateID = categoryNum;
        this.mIsAscending = isAscending;
    }

    /**
     * DatabaseError.fromStatus() is not meant to be public.
     *
     * @param params
     */

    @SuppressLint("RestrictedApi")
    @NonNull
    @Override
    public Single<LoadResult<Pair<Double, String>, DataSnapshot>> loadSingle(@NotNull LoadParams<Pair<Double, String>> params) {
        Task<DataSnapshot> task;
        Pair<Double, String> param = params.getKey();

        if (mIsAscending) {
            if (param == null) {
                Log.d(TAG, "first query startAt: " + mCateID);
                task = mQuery.orderByChild(mCategoryKey).startAt(mCateID).limitToFirst(params.getLoadSize()).get();
            } else {
                task = mQuery.orderByChild(mCategoryKey).startAt(mCateID, param.second).limitToFirst(params.getLoadSize() + 1).get();
            }
        } else {
            if (param == null) {
                Log.d(TAG, "first query startAt: " + mCateID);
                task = mQuery.orderByChild(mCategoryKey).endAt(mCateID).limitToLast(params.getLoadSize()).get();
            } else {
                task = mQuery.orderByChild(mCategoryKey).endAt(mCateID, param.second).limitToLast(params.getLoadSize() + 1).get();
            }
        }


        return Single.fromCallable(() -> {
            try {
                Tasks.await(task);
                DataSnapshot dataSnapshot = task.getResult();
                if (dataSnapshot.exists()) {

                    //Make List of DataSnapshot
                    List<DataSnapshot> data = new ArrayList<>();
                    Pair<Double, String> lastItem = null;
                    String lastKey = null;

                    if (params.getKey() == null) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Log.d(TAG, "Add initial data | cateID= " + snapshot.child(mCategoryKey).getValue(Double.class) +
                                    " | publishedDate= " + snapshot.child("publishedAt").getValue(Double.class)
                            );

                            if (mIsAscending) {
                                data.add(snapshot);
                            } else {
                                data.add(0, snapshot);
                            }
                        }
                    } else {
                        Iterator<DataSnapshot> iterator = dataSnapshot.getChildren().iterator();

                        //Skip First Item
                        if (mIsAscending && iterator.hasNext()) {
                            iterator.next();
                        }

                        while (iterator.hasNext()) {
                            DataSnapshot snapshot = iterator.next();
                            if (mIsAscending) {
                                data.add(snapshot);
                                Log.d(TAG, "Add additional data | cateID= " + snapshot.child(mCategoryKey).getValue(Double.class) +
                                        " | publishedDate= " + snapshot.child("publishedAt").getValue(Double.class)
                                );
                            } else {
                                if (iterator.hasNext()) {
                                    data.add(0, snapshot);
                                    Log.d(TAG, "Add additional data | cateID= " + snapshot.child(mCategoryKey).getValue(Double.class) +
                                            " | publishedDate= " + snapshot.child("publishedAt").getValue(Double.class)
                                    );
                                }
                            }
                        }
                    }

                    //Detect End of Data
                    if (!data.isEmpty()) {
                        //Get Last Key

                        lastKey = getLastPageKey(data);
                        Double lastCatValue = getLastPageKeyCatValue(data);
                        lastItem = new Pair<Double, String>(lastCatValue, lastKey);


                        Log.d(TAG, "lastItem: " + lastItem + ", maxCatValue: " + mCateID);
                        if (lastCatValue != null && (!lastCatValue.equals(mCateID))) {
                            // Delete wrong category item
                            lastItem = null;

                            int endIdx = data.size() - 1;
                            for (int i = endIdx; i >= 0; i--) {
                                Double catValue = data.get(i).child(mCategoryKey).getValue(Double.class);
                                if (catValue != null && !catValue.equals(mCateID)) {
                                    data.remove(i);
                                    Log.d(TAG, "Remove: " + i + ", catValue = " + catValue);
                                }
                            }
                        }
                    }
                    Log.d(TAG, "toLoadResult: lastKey: " + lastKey + ", lastItem: " + lastItem);
                    return toLoadResult(data, lastItem);
                } else {
                    String details = DETAILS_DATABASE_NOT_FOUND + mQuery.toString();
                    throw DatabaseError.fromStatus(
                            STATUS_DATABASE_NOT_FOUND,
                            MESSAGE_DATABASE_NOT_FOUND,
                            details).toException();
                }
            } catch (ExecutionException e) {
                Log.d(TAG, e.getLocalizedMessage());
                throw new Exception(e.getCause());
            }
        }).subscribeOn(Schedulers.io()).onErrorReturn(LoadResult.Error::new);
    }

    private LoadResult<Pair<Double, String>, DataSnapshot> toLoadResult(
            @NonNull List<DataSnapshot> snapshots,
            Pair<Double, String> nextPage
    ) {
        return new LoadResult.Page<>(
                snapshots,
                null, // Only paging forward.
                nextPage,
                LoadResult.Page.COUNT_UNDEFINED,
                LoadResult.Page.COUNT_UNDEFINED);
    }

    @Nullable
    private String getLastPageKey(@NonNull List<DataSnapshot> data) {
        if (data.isEmpty()) {
            return null;
        } else {
            return data.get(data.size() - 1).getKey();
        }
    }

    @Nullable
    private Double getLastPageKeyCatValue(@NonNull List<DataSnapshot> data) {
        if (data.isEmpty()) {
            return null;
        } else {
            return data.get(data.size() - 1).child(mCategoryKey).getValue(Double.class);
        }
    }

    @Nullable
    private String getLastPageValue(@NonNull List<DataSnapshot> data) {
        if (data.isEmpty()) {
            return null;
        } else {
            return data.get(data.size() - 1).getKey();
        }
    }

    @Override
    public @Nullable Pair<Double, String> getRefreshKey(@NotNull PagingState<Pair<Double, String>, DataSnapshot> pagingState) {
        return null;
    }
}
