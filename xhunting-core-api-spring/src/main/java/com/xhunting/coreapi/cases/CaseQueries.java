package com.xhunting.coreapi.cases;

import java.util.List;
import java.util.Optional;

/** Các truy vấn động theo {@link CaseFilter}; cài đặt trong {@link CaseRepositoryImpl}. */
public interface CaseQueries {

    Optional<Case> findOne(CaseFilter filter);

    List<Case> findMany(CaseFilter filter);

    long count(CaseFilter filter);
}
