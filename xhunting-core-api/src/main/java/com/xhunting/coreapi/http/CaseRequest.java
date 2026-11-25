package com.xhunting.coreapi.http;

import com.xhunting.coreapi.cases.Case;

/** Body JSON của Create/Update: {@code message CaseRequest { Context ctx; Case item; ... }}. */
public record CaseRequest(Case item) {
}
