package com.quell.api.model;

import java.util.List;

/** One page of a longer list. Pages are numbered from 0. */
public record PageView<T>(List<T> items, int page, int size, long total, int totalPages) {
}
