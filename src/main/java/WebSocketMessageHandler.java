/*
 * © 2023 Snyk Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.proxy.websocket.*;
import burp.api.montoya.websocket.Direction;
import helper.BlazorHelper;
import helper.MessageModel.GenericMessage;
import socketsleuth.WebSocketInterceptionRulesTableModel;
import static burp.api.montoya.internal.ObjectFactoryLocator.FACTORY;
import websocket.MessageProvider;

import javax.swing.*;

import org.json.JSONArray;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

class WebSocketMessageHandler implements ProxyMessageHandler {

    private final MessageProvider socketProvider;
    MontoyaApi api;
    int socketId;
    Logging logger;

    WebSocketStreamTableModel streamModel;

    JTable streamTable;

    WebSocketInterceptionRulesTableModel interceptionRules;

    WebSocketMatchReplaceRulesTableModel matchReplaceRules;

    SocketCloseCallback socketCloseCallback;
    JSONRPCResponseMonitor responseMonitor;
    WebSocketAutoRepeater webSocketAutoRepeater;
    BlazorHelper blazorHelper;

    public WebSocketMessageHandler(
            MontoyaApi api,
            int socketId,
            WebSocketStreamTableModel streamModel,
            JTable streamTable,
            WebSocketInterceptionRulesTableModel interceptionRules,
            WebSocketMatchReplaceRulesTableModel matchReplaceRules,
            SocketCloseCallback socketCloseCallback,
            JSONRPCResponseMonitor responseMonitor,
            WebSocketAutoRepeater webSocketAutoRepeater,
            MessageProvider socketProvider) {
        this.api = api;
        this.socketId = socketId;
        this.logger = api.logging();
        this.streamModel = streamModel;
        this.streamTable = streamTable;
        this.blazorHelper = new BlazorHelper(api);
        this.interceptionRules = interceptionRules;
        this.matchReplaceRules = matchReplaceRules;
        this.socketCloseCallback = socketCloseCallback;
        this.responseMonitor = responseMonitor;
        this.webSocketAutoRepeater =  webSocketAutoRepeater;
        this.socketProvider = socketProvider;
    }

    @Override
    public TextMessageReceivedAction handleTextMessageReceived(InterceptedTextMessage interceptedTextMessage) {
        // Pass received message to socket message provider
        this.socketProvider.handleTextMessage(this.socketId, interceptedTextMessage);

        // Handle message detections
        this.responseMonitor.processResponse(interceptedTextMessage.payload());

        // Handle autorepeater rules
        this.webSocketAutoRepeater.onMessageReceived(this.socketId, new InterceptedMessageFacade(interceptedTextMessage));

        int selectedRowIndex = streamTable.getSelectedRow();
        ListSelectionModel selectionModel = streamTable.getSelectionModel();
        boolean isSelectionEmpty = selectionModel.isSelectionEmpty();

        streamModel.addStream(new WebSocketStream(
                streamModel.getRowCount(),
                interceptedTextMessage,
                LocalDateTime.now(),
                ""
        ));

        // Restore the selection if there was a previous selection
        if (!isSelectionEmpty) {
            selectionModel.setSelectionInterval(selectedRowIndex, selectedRowIndex);
        } else {
            selectionModel.clearSelection();
        }

        if (shouldInterceptMessage(this.interceptionRules, interceptedTextMessage)) {
            // This is the in the API example but seems to break WSs :/
            //interceptedTextMessage.annotations().setHighlightColor(HighlightColor.RED);
            return TextMessageReceivedAction.intercept(interceptedTextMessage);
        }

        if (shouldDropMessage(this.matchReplaceRules, new InterceptedMessageFacade(interceptedTextMessage))) {
            return TextMessageReceivedAction.drop();
        }

        interceptedTextMessage = (InterceptedTextMessage) handleMatchAndReplace(
                this.matchReplaceRules,
                new InterceptedMessageFacade(interceptedTextMessage)
        );

        return TextMessageReceivedAction.continueWith(interceptedTextMessage);
    }

    private boolean shouldDropMessage(
            WebSocketMatchReplaceRulesTableModel matchReplaceRules,
            InterceptedMessageFacade interceptedMessageFacade
    ) {
        for (int i = 0; i < matchReplaceRules.getRowCount(); i++) {
            boolean enabled = (boolean) matchReplaceRules.getValueAt(i, 0); // ENABLED

            if (!enabled) {
                continue;
            }

            WebSocketMatchReplaceRulesTableModel.MatchType matchType =
                    (WebSocketMatchReplaceRulesTableModel.MatchType) matchReplaceRules.getValueAt(i, 1);

            WebSocketMatchReplaceRulesTableModel.Direction direction =
                    (WebSocketMatchReplaceRulesTableModel.Direction) matchReplaceRules.getValueAt(i, 2);

            String strMatch = (String) matchReplaceRules.getValueAt(i, 3);

            if (matchType != WebSocketMatchReplaceRulesTableModel.MatchType.DROP) {
                continue;
            }

            if (direction == WebSocketMatchReplaceRulesTableModel.Direction.CLIENT_TO_SERVER) {
                if (!interceptedMessageFacade.direction().toString().equals("CLIENT_TO_SERVER")) continue;
            }

            if (direction == WebSocketMatchReplaceRulesTableModel.Direction.SERVER_TO_CLIENT) {
                if (!interceptedMessageFacade.direction().toString().equals("SERVER_TO_CLIENT")) continue;
            }

            // Find / Match is in Hex
            if (Utils.isHexString(strMatch)) {
                return Utils.byteArrayContains(
                        interceptedMessageFacade.binaryPayload(),
                        Utils.hexStringToByteArray(strMatch)
                );
            // Otherwise normal string
            } else {
                return interceptedMessageFacade.stringPayload().contains(strMatch);
            }
        }
        return false;
    }

    private Object handleMatchAndReplace(
            WebSocketMatchReplaceRulesTableModel matchReplaceRules,
            InterceptedMessageFacade interceptedMessageFacade
    ) {
        for (int i = 0; i < matchReplaceRules.getRowCount(); i++) {
            boolean enabled = (boolean) matchReplaceRules.getValueAt(i, 0); // ENABLED

            if (!enabled) {
                continue;
            }
            WebSocketMatchReplaceRulesTableModel.MatchType matchType =
                    (WebSocketMatchReplaceRulesTableModel.MatchType) matchReplaceRules.getValueAt(i, 1);

            WebSocketMatchReplaceRulesTableModel.Direction direction =
                    (WebSocketMatchReplaceRulesTableModel.Direction) matchReplaceRules.getValueAt(i, 2);

            String strMatch = (String) matchReplaceRules.getValueAt(i, 3);
            logger.logToOutput("strMatch hereeeeeeeeee: " + strMatch);

            String strReplace = (String) matchReplaceRules.getValueAt(i, 4);

            if (matchType != WebSocketMatchReplaceRulesTableModel.MatchType.REPLACE) {
                // dropping messages needs to return a different function from the handler
                // handle this separately inside handleTextMessageReceived + handleBinaryMessageReceived
                continue;
            }

            if (direction == WebSocketMatchReplaceRulesTableModel.Direction.CLIENT_TO_SERVER) {
                if (!interceptedMessageFacade.direction().toString().equals("CLIENT_TO_SERVER")) continue;
            }

            if (direction == WebSocketMatchReplaceRulesTableModel.Direction.SERVER_TO_CLIENT) {
                if (!interceptedMessageFacade.direction().toString().equals("SERVER_TO_CLIENT")) continue;
            }

            // Are we match / replacing a hex pattern?
            if (Utils.isHexString(strMatch)) {
                logger.logToOutput("It is hex stringggggggggggggggg");
                byte[] bytesMatch = Utils.hexStringToByteArray(strMatch);
                byte[] bytesReplace = Utils.isHexString(strReplace) ? Utils.hexStringToByteArray(strReplace) : strReplace.getBytes();
                byte[] inputBytes = interceptedMessageFacade.binaryPayload();
                byte[] modified = Utils.replace(inputBytes, bytesMatch, bytesReplace);
                interceptedMessageFacade.setBytesPayload(modified);
                return interceptedMessageFacade.getInterceptedMessage();
            } else {        // it's a normal string or regex replacement
                // Do the replacement and see if there is changes
                String newStr = Utils.replace(interceptedMessageFacade.stringPayload(), strMatch, strReplace);
                if (!newStr.equals(interceptedMessageFacade.stringPayload())) {
                    interceptedMessageFacade.setStringPayload(newStr);
                    return interceptedMessageFacade.getInterceptedMessage();
                }
            }
        }
        return interceptedMessageFacade.getInterceptedMessage();
    }

    private boolean shouldInterceptMessage(
            WebSocketInterceptionRulesTableModel interceptionRules,
            InterceptedTextMessage interceptedTextMessage
    ) {
        for (int i = 0; i < interceptionRules.getRowCount(); i++) {
            boolean enabled = (boolean) interceptionRules.getValueAt(i, 0);

            WebSocketInterceptionRulesTableModel.MatchType matchType
                    = (WebSocketInterceptionRulesTableModel.MatchType) interceptionRules.getValueAt(i, 1);

            WebSocketInterceptionRulesTableModel.Direction direction
                    = (WebSocketInterceptionRulesTableModel.Direction) interceptionRules.getValueAt(i, 2);


            String condition = (String) interceptionRules.getValueAt(i, 3);

            // ignore disabled rules
            if (enabled) {
                boolean shouldIntercept = false;

                // check direction before condition - probably a better way to test this
                if (direction == WebSocketInterceptionRulesTableModel.Direction.CLIENT_TO_SERVER) {
                    if (!interceptedTextMessage.direction().toString().equals("CLIENT_TO_SERVER")) continue;
                }

                if (direction == WebSocketInterceptionRulesTableModel.Direction.SERVER_TO_CLIENT) {
                    if (!interceptedTextMessage.direction().toString().equals("SERVER_TO_CLIENT")) continue;
                }

                api.logging().logToOutput("direction rule: " + direction + " - message direction" + interceptedTextMessage.direction().toString());

                switch (matchType) {
                    case CONTAINS: {
                        shouldIntercept = interceptedTextMessage.payload().contains(condition);
                        break;
                    }
                    case DOES_NOT_CONTAIN: {
                        shouldIntercept = !interceptedTextMessage.payload().contains(condition);
                        break;
                    }
                    case EXACT_MATCH: {
                        shouldIntercept = interceptedTextMessage.payload().equals(condition);
                       break;
                    }
                    default: {
                        api.logging().logToOutput("Unknown interceptor match type detected!");
                        break;
                    }
                }

                // Don't need to test additional rules if we already matched
                if (shouldIntercept) return true;
            }
        }

        return false;
    }

    @Override
    public TextMessageToBeSentAction handleTextMessageToBeSent(InterceptedTextMessage interceptedTextMessage) {
        return TextMessageToBeSentAction.continueWith(interceptedTextMessage);
    }

    @Override
    public BinaryMessageReceivedAction handleBinaryMessageReceived(InterceptedBinaryMessage interceptedBinaryMessage) {
        logger.logToOutput("========================================================");
        // Pass received message to socket message provider
        this.socketProvider.handleBinaryMessage(this.socketId, interceptedBinaryMessage);

        // fixed value to debug
        // strMatch = "SGAAS54011";
        // strReplace = "SGABJ0899999";
        
        // handle binary here
        ByteArray payload = interceptedBinaryMessage.payload();
        ByteArray newMessage = payload;
        Direction direction = interceptedBinaryMessage.direction();

        
        if(direction == Direction.CLIENT_TO_SERVER) {
            // unpack blazor here
            ArrayList<GenericMessage> messages = this.blazorHelper.blazorUnpack(payload.getBytes());
            String messagesStr = this.blazorHelper.messageArrayToString(messages);
            logger.logToOutput("[unpack] - " + messagesStr);
            // replace here
            for (int i = 0; i < matchReplaceRules.getRowCount(); i++) {
                // get match and repalce data
                String strMatch = (String) matchReplaceRules.getValueAt(i, 3); // match condition
                String strReplace = (String) matchReplaceRules.getValueAt(i, 4); // replace with 
                boolean enabled = (boolean) matchReplaceRules.getValueAt(i, 0); // ENABLED
                if (enabled) {
                    messagesStr = messagesStr.replaceAll(strMatch, strReplace);
                }
                
            }
            logger.logToOutput("[log] " + messagesStr);
            // package blazor here
            JSONArray packMessage = new JSONArray(messagesStr); 
            byte[] blazorBytes = this.blazorHelper.blazorPack(packMessage);
            newMessage = ByteArray.byteArray(blazorBytes);
            
        } else {
            logger.logToOutput("B <<==========");
        }
        
        logger.logToOutput("New: " + newMessage);
        int selectedRowIndex = streamTable.getSelectedRow();
        ListSelectionModel selectionModel = streamTable.getSelectionModel();
        boolean isSelectionEmpty = selectionModel.isSelectionEmpty();

        streamModel.addStream(new WebSocketStream(
                streamModel.getRowCount(),
                interceptedBinaryMessage,
                LocalDateTime.now(),
                ""
        ));

        // Restore the selection if there was a previous selection
        if (!isSelectionEmpty) {
            selectionModel.setSelectionInterval(selectedRowIndex, selectedRowIndex);
        } else {
            selectionModel.clearSelection();
        }
        return BinaryMessageReceivedAction.continueWith(newMessage);
    }

    @Override
    public BinaryMessageToBeSentAction handleBinaryMessageToBeSent(InterceptedBinaryMessage interceptedBinaryMessage) {
        return BinaryMessageToBeSentAction.continueWith(interceptedBinaryMessage);
    }

    @Override
    public void onClose() {
        ProxyMessageHandler.super.onClose();
        api.logging().logToOutput("socket is closing");
        this.socketCloseCallback.handleConnectionClosed();
    }
}
