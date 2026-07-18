package xac.cloud.service;

import io.grpc.stub.StreamObserver;
import xac.cloud.config.CloudConfig;
import xac.cloud.engine.MillenniumEngine;
import xac.cloud.engine.OnnxEngine;
import xac.cloud.engine.VerdictAggregator;
import win.ac.x.ml.logic.Logger;
import xac.cloud.proto.*;
import xac.cloud.session.PlayerSession;
import xac.cloud.session.PlayerSessionManager;
import win.ac.x.vectors.Vec2f;

import java.util.ArrayList;
import java.util.List;

public class MLInferenceServiceImpl extends MLInferenceGrpc.MLInferenceImplBase {

    private final PlayerSessionManager sessionManager;
    private final MillenniumEngine millenniumEngine;
    private final OnnxEngine onnxEngine;
    private final VerdictAggregator aggregator;
    private final CloudConfig config;

    public MLInferenceServiceImpl(PlayerSessionManager sessionManager,
                                   MillenniumEngine millenniumEngine,
                                   OnnxEngine onnxEngine,
                                   CloudConfig config) {
        this.sessionManager = sessionManager;
        this.millenniumEngine = millenniumEngine;
        this.onnxEngine = onnxEngine;
        this.aggregator = new VerdictAggregator(config.getOnnxThreshold());
        this.config = config;
    }

    @Override
    public StreamObserver<ClientMessage> processPlayerStream(StreamObserver<ServerMessage> responseObserver) {
        return new StreamObserver<ClientMessage>() {
            private String currentServerId = "";
            private String currentPlayerId = "";
            private PlayerSession session = null;

            @Override
            public void onNext(ClientMessage msg) {
                currentServerId = msg.getServerId();
                currentPlayerId = msg.getPlayerId();
                session = sessionManager.getOrCreate(currentServerId, currentPlayerId);

                switch (msg.getPayloadCase()) {
                    case ROTATION:
                        handleRotation(msg.getRotation());
                        break;
                    case ATTACK:
                        handleAttack(msg.getAttack());
                        break;
                    case MOVEMENT:
                        handleMovement(msg.getMovement());
                        break;
                    case DISCONNECT:
                        handleDisconnect();
                        break;
                    default:
                        break;
                }
            }

            private void handleRotation(RotationData rot) {
                long now = System.currentTimeMillis();
                session.addRotation(rot.getYawDelta(), rot.getPitchDelta());

                List<CheckVerdict> verdicts = new ArrayList<>();

                // Attack window check - only run ML checks if recent attack
                if (session.isAttackActive(now, 3000)) {
                    // Millennium Aim ML - Legacy (7 V4.5 models)
                    if (session.isLegacyReady()) {
                        List<Vec2f> rotations = session.drainRawRotations();
                        MillenniumEngine.MillenniumResult legacyResult = millenniumEngine.runLegacyCheck(rotations);
                        if (legacyResult.isFlagged()) {
                            verdicts.add(aggregator.buildMillenniumVerdict("aim_ml_v4", legacyResult));
                        }
                    }

                    // Millennium Aim ML - RNN
                    if (session.isRNNReady()) {
                        List<Vec2f> rotations = session.drainRnnRotations();
                        MillenniumEngine.MillenniumResult rnnResult = millenniumEngine.runRNNCheck(rotations);
                        if (rnnResult.isFlagged()) {
                            verdicts.add(aggregator.buildMillenniumVerdict("aim_ml_rnn", rnnResult));
                        }
                    }

                    // ONNX Baseline
                    if (onnxEngine.isBaselineReady()) {
                        session.feedBaseline(
                                rot.getYawDelta(), rot.getPitchDelta(),
                                0f, 0f // hSpeed and clickInterval filled via movement/attack events
                        );
                        if (session.isBaselineReady()) {
                            List<OnnxEngine.ModelResult> results = onnxEngine.runBaseline(session.getBaselineBuffer());
                            verdicts.addAll(aggregator.buildOnnxVerdicts("onnx_baseline", results));
                        }
                    }
                } else {
                    // No recent attack - clear stale rotation data
                    session.clearStaleRotations();
                }

                if (!verdicts.isEmpty()) {
                    ServerMessage response = ServerMessage.newBuilder()
                            .setPlayerId(currentPlayerId)
                            .addAllVerdicts(verdicts)
                            .build();
                    responseObserver.onNext(response);
                }
            }

            private void handleAttack(AttackEvent attack) {
                long now = attack.getTimestamp() > 0 ? attack.getTimestamp() : System.currentTimeMillis();
                session.onAttack(now);
            }

            private void handleMovement(MoveData move) {
                // Update horizontal speed for baseline
                session.setLastHorizontalSpeed(move.getHorizontalSpeed());
                session.feedRecentHSpeed(move.getHorizontalSpeed());

                // ONNX Physics check
                if (onnxEngine.isPhysicsReady()) {
                    float[] features = new float[10];
                    features[0] = move.getTickDrift();
                    features[1] = move.getHorizontalSpeed();
                    features[2] = move.getVerticalSpeed();
                    features[3] = move.getExpectedMaxHorizontal();
                    features[4] = move.getAirTicks();
                    features[5] = move.getAcceleration();
                    features[6] = move.getSpeedRatio();
                    features[7] = move.getGroundState();
                    features[8] = move.getSprintState();
                    features[9] = move.getSpeedVariance() != 0f ? move.getSpeedVariance() : session.computeSpeedVariance();

                    session.feedPhysics(features);

                    if (session.isPhysicsReady()) {
                        List<OnnxEngine.ModelResult> results = onnxEngine.runPhysics(session.getPhysicsBuffer());
                        List<CheckVerdict> verdicts = aggregator.buildOnnxVerdicts("onnx_physics", results);

                        boolean hasFlags = false;
                        for (CheckVerdict v : verdicts) {
                            if (!"CLEAN".equals(v.getVerdict())) {
                                hasFlags = true;
                                break;
                            }
                        }

                        if (hasFlags) {
                            ServerMessage response = ServerMessage.newBuilder()
                                    .setPlayerId(currentPlayerId)
                                    .addAllVerdicts(verdicts)
                                    .build();
                            responseObserver.onNext(response);
                        }
                    }
                }
            }

            private void handleDisconnect() {
                sessionManager.removePlayer(currentServerId, currentPlayerId);
                session = null;
            }

            @Override
            public void onError(Throwable t) {
                if (session != null) {
                    sessionManager.removePlayer(currentServerId, currentPlayerId);
                }
                Logger.warn("Stream error for " + currentPlayerId + ": " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                if (session != null) {
                    sessionManager.removePlayer(currentServerId, currentPlayerId);
                }
                responseObserver.onCompleted();
            }
        };
    }
}
