package com.ducky.firebaseuipagingcategory.categorypaging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.paging.PagingData;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.database.SnapshotParser;
import com.firebase.ui.database.paging.DatabasePagingOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

/**
 * Paginated RecyclerView Adapter for a Firebase Realtime Database query.
 *
 * Configured with {@link CategoryPagingOptions}.
 */
public abstract class CategoryFirebaseRecyclerPagingAdapter<T, VH extends RecyclerView.ViewHolder>
        extends PagingDataAdapter<DataSnapshot, VH>
        implements LifecycleObserver {

    private boolean useOrigial = true;
    private CategoryPagingOptions<T> mOptions;
    private DatabasePagingOptions<T> mOrigialOptions;
    private SnapshotParser<T> mParser;
    private LiveData<PagingData<DataSnapshot>> mPagingData;

    //Data Observer
    private final Observer<PagingData<DataSnapshot>> mDataObserver = new Observer<PagingData<DataSnapshot>>() {
        @Override
        public void onChanged(@Nullable PagingData<DataSnapshot> snapshots) {
            if (snapshots == null) {
                return;
            }
            if (useOrigial) {
                submitData(mOrigialOptions.getOwner().getLifecycle(), snapshots);
            } else {
                submitData(mOptions.getOwner().getLifecycle(), snapshots);
            }
        }
    };

    /**
     * Construct a new FirestorePagingAdapter from the given {@link com.firebase.ui.database.paging.DatabasePagingOptions}.
     */
    public CategoryFirebaseRecyclerPagingAdapter(@NonNull CategoryPagingOptions<T> options){
        super(options.getDiffCallback());
        useOrigial = false;
        mOptions = options;
        init();
    }

    public CategoryFirebaseRecyclerPagingAdapter(@NonNull DatabasePagingOptions<T> options){
        super(options.getDiffCallback());
        useOrigial = true;
        mOrigialOptions = options;

        init();
    }

    /**
     * Initializes Snapshots and LiveData
     */
    public void init() {
        if (useOrigial) {
            mPagingData = mOrigialOptions.getData();

            mParser = mOrigialOptions.getParser();

            if (mOrigialOptions.getOwner() != null) {
                mOrigialOptions.getOwner().getLifecycle().addObserver(this);
            }
        } else {
            mPagingData = mOptions.getData();

            mParser = mOptions.getParser();

            if (mOptions.getOwner() != null) {
                mOptions.getOwner().getLifecycle().addObserver(this);
            }
        }

    }

    /**
     * Re-initialize the Adapter with a new set of options. Can be used to change the query without
     * re-constructing the entire adapter.
     */
    public void updateOptions(@NonNull CategoryPagingOptions<T> options) {
        mOptions = options;

        // Tear down old options
        boolean hasObservers = mPagingData.hasObservers();
        if (mOptions.getOwner() != null) {
            mOptions.getOwner().getLifecycle().removeObserver(this);
        }
        stopListening();

        // Reinit Options
        init();

        if (hasObservers) {
            startListening();
        }
    }

    public void updateOptions(@NonNull DatabasePagingOptions<T> options) {
        mOrigialOptions = options;

        // Tear down old options
        boolean hasObservers = mPagingData.hasObservers();
        if (mOrigialOptions.getOwner() != null) {
            mOrigialOptions.getOwner().getLifecycle().removeObserver(this);
        }
        stopListening();

        // Reinit Options
        init();

        if (hasObservers) {
            startListening();
        }
    }

    /**
     * Start listening to paging / scrolling events and populating adapter data.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void startListening() {
        mPagingData.observeForever(mDataObserver);
    }

    /**
     * Unsubscribe from paging / scrolling events, no more data will be populated, but the existing
     * data will remain.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void stopListening() {
        mPagingData.removeObserver(mDataObserver);
    }

    @Override
    public void onBindViewHolder(@NonNull VH viewHolder, int position) {
        DataSnapshot snapshot = getItem(position);
        onBindViewHolder(viewHolder, position, mParser.parseSnapshot(snapshot));
    }

    /**
     * @param model the model object containing the data that should be used to populate the view.
     * @see #onBindViewHolder(RecyclerView.ViewHolder, int)
     */
    protected abstract void onBindViewHolder(@NonNull VH viewHolder, int position, @NonNull T model);

    @NonNull
    public DatabaseReference getRef(int position){
       return getItem(position).getRef();
    }

}
