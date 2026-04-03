package com.masterofpuppets.voxpop.ui;

import com.masterofpuppets.voxpop.network.models.QueueUpdate;
import com.masterofpuppets.voxpop.network.signaling.SignalingServer;
import com.masterofpuppets.voxpop.session.Participant;
import com.masterofpuppets.voxpop.session.SessionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.stream.Collectors;

public class ModeratorController {

    @FXML private Label currentSpeakerLabel;
    @FXML private ListView<String> participantsListView;
    @FXML private ListView<QueueItemUi> queueListView;
    @FXML private Button btnCloseTopic;

    private SignalingServer signalingServer;
    private SessionManager sessionManager;

    public record QueueItemUi(String sessionId, String name, boolean isSpeaking, boolean isMuted) {}

    @FXML
    public void initialize() {
        queueListView.setCellFactory(listView -> new QueueCell());
    }

    public void setDependencies(SignalingServer server, SessionManager manager) {
        this.signalingServer = server;
        this.sessionManager = manager;
    }

    @FXML
    public void onCloseTopic(ActionEvent event) {
        if (signalingServer != null) {
            signalingServer.closeTopic();
        }
    }

    public void updateQueue(List<QueueUpdate.QueueItem> newQueue) {
        Platform.runLater(() -> {
            queueListView.getItems().clear();
            for (QueueUpdate.QueueItem item : newQueue) {
                boolean isSpeaking = false;
                boolean isMuted = false;

                if (sessionManager != null) {
                    Participant p = sessionManager.getParticipantBySessionId(item.sessionId());
                    if (p != null) {
                        isSpeaking = p.isSpeaking();
                        isMuted = p.isMuted();
                    }
                }
                queueListView.getItems().add(new QueueItemUi(item.sessionId(), item.name(), isSpeaking, isMuted));
            }
            updateCurrentSpeakerLabel();
        });
    }

    public void updateParticipants(List<Participant> participants) {
        Platform.runLater(() -> {
            participantsListView.getItems().clear();
            for (Participant p : participants) {
                participantsListView.getItems().add(p.getName());
            }
            updateCurrentSpeakerLabel();
        });
    }

    private void updateCurrentSpeakerLabel() {
        if (sessionManager == null) return;

        List<Participant> speakers = sessionManager.getActiveSpeakers();
        if (speakers.isEmpty()) {
            currentSpeakerLabel.setText("None");
        } else {
            String names = speakers.stream()
                    .map(Participant::getName)
                    .collect(Collectors.joining(", "));
            currentSpeakerLabel.setText(names);
        }
    }

    private class QueueCell extends ListCell<QueueItemUi> {
        private final HBox root = new HBox(10);
        private final Label nameLabel = new Label();
        private final Region spacer = new Region();
        private final Button btnGrant = new Button("Grant");
        private final Button btnRevoke = new Button("Revoke");
        private final Button btnMute = new Button("Mute");

        public QueueCell() {
            super();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getChildren().addAll(nameLabel, spacer, btnGrant, btnRevoke, btnMute);

            btnGrant.setOnAction(event -> {
                QueueItemUi item = getItem();
                if (item != null && signalingServer != null) {
                    signalingServer.grantSpeechToParticipant(item.sessionId());
                }
            });

            btnRevoke.setOnAction(event -> {
                QueueItemUi item = getItem();
                if (item != null && signalingServer != null) {
                    signalingServer.revokeSpeechFromParticipant(item.sessionId());
                }
            });

            btnMute.setOnAction(event -> {
                QueueItemUi item = getItem();
                if (item != null && signalingServer != null) {
                    signalingServer.toggleMuteParticipant(item.sessionId());
                }
            });
        }

        @Override
        protected void updateItem(QueueItemUi item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                nameLabel.setText(item.name());

                if (item.isSpeaking() && item.isMuted()) {
                    setStyle("-fx-background-color: #ffe8cc;"); // Laranja
                    btnGrant.setDisable(true);
                    btnRevoke.setDisable(false);
                } else if (item.isSpeaking()) {
                    setStyle("-fx-background-color: #d4edda;"); // Verde
                    btnGrant.setDisable(true);
                    btnRevoke.setDisable(false);
                } else if (item.isMuted()) {
                    setStyle("-fx-background-color: #f8d7da;"); // Vermelho
                    btnGrant.setDisable(false);
                    btnRevoke.setDisable(true);
                } else {
                    setStyle(""); // Transparente/Normal
                    btnGrant.setDisable(false);
                    btnRevoke.setDisable(true);
                }

                btnMute.setText(item.isMuted() ? "Unmute" : "Mute");
                setGraphic(root);
            }
        }
    }
}