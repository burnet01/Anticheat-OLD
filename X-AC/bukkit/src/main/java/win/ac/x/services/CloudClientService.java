package win.ac.x.services;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import win.ac.x.X;
import xac.cloud.proto.*;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class CloudClientService {

    private static String CLOUD_HOST = "127.0.0.1";
    private static int CLOUD_PORT = 50051;
    private static String SERVER_ID = "default";
    private static boolean ENABLED = true;

    private static ManagedChannel channel;
    private static MLInferenceGrpc.MLInferenceStub stub;
    private static StreamObserver<ClientMessage> requestObserver;
    private static boolean connected = false;

    private static VerdictCallback verdictCallback;

    public interface VerdictCallback {
        void onVerdict(String playerId, CheckVerdict verdict);
    }

    public static void init(String host, int port, String serverId) {
        CLOUD_HOST = host;
        CLOUD_PORT = port;
        SERVER_ID = serverId;
        connect();
    }

    public static void setEnabled(boolean enabled) {
        ENABLED = enabled;
        if (!enabled) disconnect();
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isConnected() {
        return connected;
    }

    public static void setVerdictCallback(VerdictCallback callback) {
        verdictCallback = callback;
    }

    public static synchronized void connect() {
        if (!ENABLED) return;
        if (connected) return;

        try {
            channel = NettyChannelBuilder.forAddress(new InetSocketAddress(CLOUD_HOST, CLOUD_PORT))
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(64 * 1024)
                    .build();

            stub = MLInferenceGrpc.newStub(channel);

            StreamObserver<ServerMessage> responseObserver = new StreamObserver<ServerMessage>() {
                @Override
                public void onNext(ServerMessage msg) {
                    for (CheckVerdict verdict : msg.getVerdictsList()) {
                        if (verdictCallback != null) {
                            verdictCallback.onVerdict(msg.getPlayerId(), verdict);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    connected = false;
                    X.getInstance().getLogger().warning("[XAC-Cloud] Connection error: " + t.getMessage());
                    scheduleReconnect();
                }

                @Override
                public void onCompleted() {
                    connected = false;
                    X.getInstance().getLogger().info("[XAC-Cloud] Connection closed.");
                    scheduleReconnect();
                }
            };

            requestObserver = stub.processPlayerStream(responseObserver);
            connected = true;
            X.getInstance().getLogger().info("[XAC-Cloud] Connected to " + CLOUD_HOST + ":" + CLOUD_PORT);
        } catch (Exception e) {
            X.getInstance().getLogger().log(Level.WARNING, "[XAC-Cloud] Failed to connect", e);
            scheduleReconnect();
        }
    }

    private static void scheduleReconnect() {
        X.getInstance().getServer().getScheduler().runTaskLaterAsynchronously(X.getInstance(),
                CloudClientService::connect, 100L);
    }

    public static synchronized void disconnect() {
        connected = false;
        try {
            if (requestObserver != null) requestObserver.onCompleted();
        } catch (Exception ignored) {}
        if (channel != null) channel.shutdown();
        requestObserver = null;
        channel = null;
    }

    public static void sendRotation(String playerId, float yawDelta, float pitchDelta) {
        if (!connected || requestObserver == null) return;
        ClientMessage msg = ClientMessage.newBuilder()
                .setServerId(SERVER_ID)
                .setPlayerId(playerId)
                .setRotation(RotationData.newBuilder()
                        .setYawDelta(yawDelta)
                        .setPitchDelta(pitchDelta)
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();
        requestObserver.onNext(msg);
    }

    public static void sendAttack(String playerId) {
        if (!connected || requestObserver == null) return;
        ClientMessage msg = ClientMessage.newBuilder()
                .setServerId(SERVER_ID)
                .setPlayerId(playerId)
                .setAttack(AttackEvent.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();
        requestObserver.onNext(msg);
    }

    public static void sendMovement(String playerId, float tickDrift, float hSpeed, float vSpeed,
                                     float expectedMaxH, float airTicks, float acceleration,
                                     float speedRatio, float groundState, float sprintState,
                                     float speedVariance) {
        if (!connected || requestObserver == null) return;
        ClientMessage msg = ClientMessage.newBuilder()
                .setServerId(SERVER_ID)
                .setPlayerId(playerId)
                .setMovement(MoveData.newBuilder()
                        .setTickDrift(tickDrift)
                        .setHorizontalSpeed(hSpeed)
                        .setVerticalSpeed(vSpeed)
                        .setExpectedMaxHorizontal(expectedMaxH)
                        .setAirTicks(airTicks)
                        .setAcceleration(acceleration)
                        .setSpeedRatio(speedRatio)
                        .setGroundState(groundState)
                        .setSprintState(sprintState)
                        .setSpeedVariance(speedVariance)
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();
        requestObserver.onNext(msg);
    }

    public static void sendDisconnect(String playerId) {
        if (!connected || requestObserver == null) return;
        ClientMessage msg = ClientMessage.newBuilder()
                .setServerId(SERVER_ID)
                .setPlayerId(playerId)
                .setDisconnect(PlayerDisconnect.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();
        requestObserver.onNext(msg);
    }
}
