package com.palmistrylab.api.palm.dto;

public interface AnalyzeProgressListener {

  AnalyzeProgressListener NONE = new AnalyzeProgressListener() {
  };

  default void onStage(String stage, int percent, String message) {
  }

  default void onGeneratedChars(int chars) {
  }
}
