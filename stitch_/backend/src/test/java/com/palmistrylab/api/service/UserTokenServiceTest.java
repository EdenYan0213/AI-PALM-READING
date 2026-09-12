package com.palmistrylab.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTokenServiceTest {

  private final UserTokenService userTokenService = new UserTokenService("unit-test-secret");

  @Test
  void issuedUserIdPassesVerification() {
    String userId = userTokenService.issueUserId();

    assertThat(userId).startsWith("U-").hasSize(23);
    assertThat(userTokenService.verifyUserId(userId)).isTrue();
  }

  @Test
  void tamperedUserIdFailsVerification() {
    String userId = userTokenService.issueUserId();
    String forged = userId.substring(0, userId.length() - 1)
        + (userId.endsWith("0") ? "1" : "0");

    assertThat(userTokenService.verifyUserId(forged)).isFalse();
    assertThat(userTokenService.verifyUserId("guest-demo")).isFalse();
    assertThat(userTokenService.verifyUserId(null)).isFalse();
    assertThat(userTokenService.verifyUserId("")).isFalse();
  }

  @Test
  void resolveKeepsValidPreviousAndReissuesOtherwise() {
    String valid = userTokenService.issueUserId();
    assertThat(userTokenService.resolveUserId(valid)).isEqualTo(valid);
    assertThat(userTokenService.resolveUserId("guest-demo"))
        .isNotEqualTo("guest-demo")
        .satisfies(userTokenService::verifyUserId);
  }

  @Test
  void identitiesFromDifferentSecretsAreIncompatible() {
    UserTokenService other = new UserTokenService("another-secret");
    String foreign = other.issueUserId();

    assertThat(userTokenService.verifyUserId(foreign)).isFalse();
  }
}
