package no.cantara.realestate.metasys.cloudconnector.utils;

import java.util.ArrayList;

public class LimitedArrayList<E> extends ArrayList<E> {
    private static final long serialVersionUID = -23456691722L;
    private final int limit;

    public LimitedArrayList(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E object) {
        if (this.size() > limit) return false;
        return super.add(object);
    }

}
