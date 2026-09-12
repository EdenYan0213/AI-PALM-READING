package com.palmistrylab.api.palm.dto;

public record PalmLineTraces(
    java.util.List<TracePoint> lifeLine,
    java.util.List<TracePoint> wisdomLine,
    java.util.List<TracePoint> loveLine) {
}
