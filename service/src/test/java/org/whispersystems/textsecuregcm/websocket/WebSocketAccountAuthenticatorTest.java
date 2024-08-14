/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import io.dropwizard.auth.basic.BasicCredentials;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.whispersystems.textsecuregcm.auth.AccountAuthenticator;
import org.whispersystems.textsecuregcm.auth.AuthenticatedDevice;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.HeaderUtils;
import org.whispersystems.websocket.ReusableAuth;
import org.whispersystems.websocket.auth.PrincipalSupplier;

class WebSocketAccountAuthenticatorTest {

  private static final String VALID_USER = PhoneNumberUtil.getInstance().format(
      PhoneNumberUtil.getInstance().getExampleNumber("NZ"), PhoneNumberUtil.PhoneNumberFormat.E164);

  private static final String VALID_PASSWORD = "valid";

  private static final String INVALID_USER = PhoneNumberUtil.getInstance().format(
      PhoneNumberUtil.getInstance().getExampleNumber("AU"), PhoneNumberUtil.PhoneNumberFormat.E164);

  private static final String INVALID_PASSWORD = "invalid";

  private AccountAuthenticator accountAuthenticator;

  private UpgradeRequest upgradeRequest;

  @BeforeEach
  void setUp() {
    accountAuthenticator = mock(AccountAuthenticator.class);

    when(accountAuthenticator.authenticate(eq(new BasicCredentials(VALID_USER, VALID_PASSWORD))))
        .thenReturn(Optional.of(new AuthenticatedDevice(mock(Account.class), mock(Device.class))));

    when(accountAuthenticator.authenticate(eq(new BasicCredentials(INVALID_USER, INVALID_PASSWORD))))
        .thenReturn(Optional.empty());

    upgradeRequest = mock(UpgradeRequest.class);
  }

  @ParameterizedTest
  @MethodSource
  void testAuthenticate(
      @Nullable final String authorizationHeaderValue,
      final Map<String, List<String>> upgradeRequestParameters,
      final boolean expectAccount,
      final boolean expectInvalid) throws Exception {

    when(upgradeRequest.getParameterMap()).thenReturn(upgradeRequestParameters);
    if (authorizationHeaderValue != null) {
      when(upgradeRequest.getHeader(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorizationHeaderValue);
    }

    final WebSocketAccountAuthenticator webSocketAuthenticator = new WebSocketAccountAuthenticator(
        accountAuthenticator,
        mock(PrincipalSupplier.class));

    final ReusableAuth<AuthenticatedDevice> result = webSocketAuthenticator.authenticate(upgradeRequest);

    assertEquals(expectAccount, result.ref().isPresent());
    assertEquals(expectInvalid, result.invalidCredentialsProvided());
  }

  private static Stream<Arguments> testAuthenticate() {
    final Map<String, List<String>> paramsMapWithValidAuth =
        Map.of("login", List.of(VALID_USER), "password", List.of(VALID_PASSWORD));
    final Map<String, List<String>> paramsMapWithInvalidAuth =
        Map.of("login", List.of(INVALID_USER), "password", List.of(INVALID_PASSWORD));
    final String headerWithValidAuth =
        HeaderUtils.basicAuthHeader(VALID_USER, VALID_PASSWORD);
    final String headerWithInvalidAuth =
        HeaderUtils.basicAuthHeader(INVALID_USER, INVALID_PASSWORD);
    return Stream.of(
        // if `Authorization` header is present, outcome should not depend on the value of query parameters
        Arguments.of(headerWithValidAuth, Map.of(), true, false),
        Arguments.of(headerWithInvalidAuth, Map.of(), false, true),
        Arguments.of("invalid header value", Map.of(), false, true),
        Arguments.of(headerWithValidAuth, paramsMapWithValidAuth, true, false),
        Arguments.of(headerWithInvalidAuth, paramsMapWithValidAuth, false, true),
        Arguments.of("invalid header value", paramsMapWithValidAuth, false, true),
        Arguments.of(headerWithValidAuth, paramsMapWithInvalidAuth, true, false),
        Arguments.of(headerWithInvalidAuth, paramsMapWithInvalidAuth, false, true),
        Arguments.of("invalid header value", paramsMapWithInvalidAuth, false, true),
        // if `Authorization` header is not set, outcome should match the query params based auth
        Arguments.of(null, paramsMapWithValidAuth, true, false),
        Arguments.of(null, paramsMapWithInvalidAuth, false, true),
        Arguments.of(null, Map.of(), false, false)
    );
  }
}
