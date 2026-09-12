package com.palmistrylab.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 无状态用户身份服务。
 *
 * userId 格式: U-<12位随机hex>-<8位HMAC签名前缀>
 * 优点: 无需建用户表，记录接口只凭 userId 即可校验归属，
 *       伪造他人 userId 需要服务端签名密钥。
 */
@Service
public class UserTokenService {

  private static final Pattern USER_ID_PATTERN = Pattern.compile("^U-([0-9a-f]{12})-([0-9a-f]{8})$");
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] secret;
  private final SecureRandom secureRandom = new SecureRandom();

  public UserTokenService(@Value("${app.user-token.secret:palmistry-local-dev-secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /** 签发全新身份。 */
  public String issueUserId() {
    byte[] random = new byte[6];
    secureRandom.nextBytes(random);
    String randomPart = HexFormat.of().formatHex(random);
    return "U-" + randomPart + "-" + sign(randomPart);
  }

  /**
   * 引导身份: previous 有效则续用（老用户无感迁移），否则签发新身份。
   */
  public String resolveUserId(String previous) {
    if (previous != null && !previous.isBlank() && verifyUserId(previous)) {
      return previous;
    }
    return issueUserId();
  }

  /** 面向 /user/identity 端点的响应组装。 */
  public com.palmistrylab.api.web.dto.UserIdentityResponse resolveIdentity(String previous) {
    boolean restored = previous != null && !previous.isBlank() && verifyUserId(previous);
    return new com.palmistrylab.api.web.dto.UserIdentityResponse(resolveUserId(previous), restored);
  }

  /** 记录模块统一入口校验：拒绝伪造或未知来源的 userId。 */
  public String requireValidUserId(String userId) {
    if (!verifyUserId(userId)) {
      throw new IllegalArgumentException("用户身份无效，请重新获取身份后使用");
    }
    return userId;
  }

  /** 校验 userId 是否由本服务签发且未被篡改。 */
  public boolean verifyUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return false;
    }
    var matcher = USER_ID_PATTERN.matcher(userId);
    if (!matcher.matches()) {
      return false;
    }
    String randomPart = matcher.group(1);
    String signature = matcher.group(2);
    return MessageDigest.isEqual(
        signature.getBytes(StandardCharsets.US_ASCII),
        sign(randomPart).getBytes(StandardCharsets.US_ASCII));
  }

  private String sign(String randomPart) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      byte[] digest = mac.doFinal(randomPart.getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(digest).substring(0, 8);
    } catch (Exception ex) {
      throw new IllegalStateException("无法计算用户签名", ex);
    }
  }
}
