package com.rushd.market;

final class MarketFailure extends RuntimeException {
    private final int status;
    MarketFailure(int status, String message) { super(message); this.status = status; }
    int status() { return status; }
    static MarketFailure invalid(String message) { return new MarketFailure(400, message); }
    static MarketFailure conflict() { return new MarketFailure(409, "تعارض في النسخة أو الهوية؛ أعد القراءة قبل المحاولة، ولن تستبدل بيانات محفوظة."); }
    static MarketFailure missing() { return new MarketFailure(404, "المصدر أو المرجع غير موجود."); }
}
