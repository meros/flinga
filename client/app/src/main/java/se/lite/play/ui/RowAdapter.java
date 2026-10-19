package se.lite.play.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

/**
 * ListView adapter that recycles {@link RowView}s. Keep one instance per ListView and call
 * {@link #setItems} instead of creating a new adapter per screen.
 */
public final class RowAdapter<T> extends BaseAdapter {

    /** Fills a recycled, already reset row for one item. */
    public interface Binder<T> {
        void bind(T item, RowView row);
    }

    /** Says whether an item can take the selection; group headers cannot. */
    public interface Selectable<T> {
        boolean selectable(T item);
    }

    /** F16: binds an item drawn as a row of pills (Serie actions and season chips). */
    public interface PillBinder<T> {
        void bind(T item, PillsView pills);
    }

    private Selectable<T> selectable;
    private Selectable<T> isPills;
    private PillBinder<T> pillBinder;

    /** Items for which {@code isPills} is true are drawn by a {@link PillsView} bound with {@code binder}. */
    public void setPills(Selectable<T> isPills, PillBinder<T> binder) {
        this.isPills = isPills;
        this.pillBinder = binder;
    }
    private List<T> items;
    private final Binder<T> binder;

    public RowAdapter(List<T> items, Binder<T> binder) {
        this.items = items;
        this.binder = binder;
    }

    public void setItems(List<T> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setSelectable(Selectable<T> s) {
        this.selectable = s;
    }

    /** The first position that can take the selection, or -1. */
    public int firstSelectable() {
        for (int i = 0; i < getCount(); i++) if (isEnabled(i)) return i;
        return -1;
    }

    @Override public boolean areAllItemsEnabled() { return selectable == null; }
    @Override public boolean isEnabled(int i) { return selectable == null || selectable.selectable(items.get(i)); }

    public List<T> getItems() {
        return items;
    }

    @Override public int getCount() { return items == null ? 0 : items.size(); }
    @Override public T getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }
    @Override public int getViewTypeCount() { return 2; }

    @Override
    public int getItemViewType(int i) {
        return isPills != null && isPills.selectable(items.get(i)) ? 1 : 0;
    }

    @Override
    public View getView(int i, View convert, ViewGroup parent) {
        if (getItemViewType(i) == 1) {
            PillsView p = convert instanceof PillsView ? (PillsView) convert : new PillsView(parent.getContext());
            pillBinder.bind(items.get(i), p);
            return p;
        }
        RowView row = convert instanceof RowView ? (RowView) convert : new RowView(parent.getContext());
        row.reset();
        binder.bind(items.get(i), row);
        row.first(i == 0);
        return row;
    }
}
