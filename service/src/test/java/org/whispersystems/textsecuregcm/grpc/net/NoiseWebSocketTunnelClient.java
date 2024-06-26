package org.whispersystems.textsecuregcm.grpc.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.SocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.function.Supplier;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.http.HttpHeaders;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import javax.annotation.Nullable;

class NoiseWebSocketTunnelClient implements AutoCloseable {

  private final ServerBootstrap serverBootstrap;
  private Channel serverChannel;

  static final URI AUTHENTICATED_WEBSOCKET_URI = URI.create("wss://localhost/authenticated");
  static final URI ANONYMOUS_WEBSOCKET_URI = URI.create("wss://localhost/anonymous");

  public NoiseWebSocketTunnelClient(final SocketAddress remoteServerAddress,
      final URI websocketUri,
      final boolean authenticated,
      final ECKeyPair ecKeyPair,
      final ECPublicKey rootPublicKey,
      @Nullable final UUID accountIdentifier,
      final byte deviceId,
      final HttpHeaders headers,
      final boolean useTls,
      @Nullable final X509Certificate trustedServerCertificate,
      @Nullable final Supplier<HAProxyMessage> proxyMessageSupplier,
      final NioEventLoopGroup eventLoopGroup,
      final WebSocketCloseListener webSocketCloseListener) {

    this.serverBootstrap = new ServerBootstrap()
        .localAddress(new LocalAddress("websocket-noise-tunnel-client"))
        .channel(LocalServerChannel.class)
        .group(eventLoopGroup)
        .childHandler(new ChannelInitializer<LocalChannel>() {
          @Override
          protected void initChannel(final LocalChannel localChannel) {
            localChannel.pipeline().addLast(new EstablishRemoteConnectionHandler(useTls,
                trustedServerCertificate,
                websocketUri,
                authenticated,
                ecKeyPair,
                rootPublicKey,
                accountIdentifier,
                deviceId,
                headers,
                remoteServerAddress,
                webSocketCloseListener,
                proxyMessageSupplier));
          }
        });
  }

  LocalAddress getLocalAddress() {
    return (LocalAddress) serverChannel.localAddress();
  }

  NoiseWebSocketTunnelClient start() throws InterruptedException {
    serverChannel = serverBootstrap.bind().await().channel();
    return this;
  }

  @Override
  public void close() throws InterruptedException {
    serverChannel.close().await();
  }
}
