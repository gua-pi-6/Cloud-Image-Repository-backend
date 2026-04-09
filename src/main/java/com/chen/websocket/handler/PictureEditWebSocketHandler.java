package com.chen.websocket.handler;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.chen.constant.PictureCollabConstant;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.model.dto.picture.collab.PictureCollabApplyRequest;
import com.chen.model.dto.picture.collab.PictureCollabApplyResult;
import com.chen.model.dto.picture.collab.PictureCollabStateVo;
import com.chen.service.PictureCollabService;
import com.chen.websocket.model.PictureEditClientMessage;
import com.chen.websocket.model.PictureEditServerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
/**
 * 图片协同编辑 WebSocket 主处理器。
 *
 * <p>它负责的事情可以概括为三层：
 * 1. 房间管理：按 pictureId 维护在线连接；
 * 2. 协议分发：识别 JOIN / PING / OP；
 * 3. 消息广播：把 service 层产出的权威结果发给当前连接和同房间其他连接。
 *
 * <p>真正的状态演算不放在这里，而是委托给 PictureCollabService，
 * 这样 handler 保持轻量，便于你从“协议层”切换到“业务层”逐层理解。
 */
public class PictureEditWebSocketHandler extends TextWebSocketHandler {

    /**
     * 房间维度的连接集合：一张图片对应一个在线协作者集合。
     */
    private final Map<Long, CopyOnWriteArraySet<WebSocketSession>> roomSessionMap = new ConcurrentHashMap<>();

    /**
     * 反向索引：sessionId -> pictureId，便于断开连接时快速回收。
     */
    private final Map<String, Long> sessionRoomMap = new ConcurrentHashMap<>();

    @Resource
    private PictureCollabService pictureCollabService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 握手阶段已经把 pictureId 放进了 session attribute，这里直接复用。
        Long pictureId = getSessionPictureId(session);
        if (pictureId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 建立连接后立刻加入图片房间，并把当前权威状态推给新连接。
        roomSessionMap.computeIfAbsent(pictureId, key -> new CopyOnWriteArraySet<>()).add(session);
        sessionRoomMap.put(session.getId(), pictureId);
        sendSyncState(session, pictureId);
        broadcastOnlineCount(pictureId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 协议层只做 JSON 反序列化和消息路由，避免业务逻辑散落在这里。
        PictureEditClientMessage clientMessage;
        try {
            clientMessage = objectMapper.readValue(message.getPayload(), PictureEditClientMessage.class);
        } catch (Exception exception) {
            sendErrorMessage(session, ErrorCode.PARAMS_ERROR.getCode(), "消息格式错误", null);
            return;
        }
        if (clientMessage == null || StrUtil.isBlank(clientMessage.getType())) {
            sendErrorMessage(session, ErrorCode.PARAMS_ERROR.getCode(), "消息类型不能为空", null);
            return;
        }
        String messageType = clientMessage.getType();
        if (PictureCollabConstant.MESSAGE_PING.equals(messageType)) {
            sendPong(session);
            return;
        }
        if (PictureCollabConstant.MESSAGE_JOIN.equals(messageType)) {
            // JOIN 既可以用于首次进入，也可以用于客户端发现版本跳跃后的重同步。
            Long pictureId = getSessionPictureId(session);
            if (pictureId != null) {
                sendSyncState(session, pictureId);
                sendOnlineCount(session, pictureId);
            }
            return;
        }
        if (PictureCollabConstant.MESSAGE_OP.equals(messageType)) {
            handleOperation(session, clientMessage);
            return;
        }
        sendErrorMessage(session, ErrorCode.PARAMS_ERROR.getCode(), "未知消息类型", clientMessage.getOpId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 断开连接后要同步更新在线人数，否则其他用户看到的人数会一直偏大。
        Long pictureId = removeSession(session, true);
        if (pictureId != null) {
            broadcastOnlineCount(pictureId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket 连接异常, sessionId={}", session.getId(), exception);
        Long pictureId = removeSession(session, true);
        if (pictureId != null) {
            broadcastOnlineCount(pictureId);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void handleOperation(WebSocketSession session, PictureEditClientMessage clientMessage) {
        Long pictureId = getSessionPictureId(session);
        Long userId = getSessionUserId(session);
        if (pictureId == null || userId == null) {
            sendErrorMessage(session, ErrorCode.NO_AUTH_ERROR.getCode(), "会话鉴权失效", clientMessage.getOpId());
            return;
        }

        // 双重校验 pictureId，避免客户端伪造消息把操作打到别的图片房间。
        if (clientMessage.getPictureId() != null && !ObjUtil.equals(clientMessage.getPictureId(), pictureId)) {
            sendErrorMessage(session, ErrorCode.PARAMS_ERROR.getCode(), "pictureId 与当前会话不一致", clientMessage.getOpId());
            return;
        }

        // 到这里再把协议对象转换成服务层请求对象。
        PictureCollabApplyRequest applyRequest = new PictureCollabApplyRequest();
        applyRequest.setPictureId(pictureId);
        applyRequest.setUserId(userId);
        applyRequest.setOpId(clientMessage.getOpId());
        applyRequest.setBaseRevision(clientMessage.getBaseRevision());
        applyRequest.setOpType(clientMessage.getOpType());
        applyRequest.setValue(clientMessage.getValue());
        applyRequest.setCropX(clientMessage.getCropX());
        applyRequest.setCropY(clientMessage.getCropY());
        applyRequest.setCropWidth(clientMessage.getCropWidth());
        applyRequest.setCropHeight(clientMessage.getCropHeight());
        try {
            PictureCollabApplyResult applyResult = pictureCollabService.applyOperation(applyRequest);
            sendAck(session, applyResult);

            // 幂等回放命中时只给当前客户端 ACK，不再次广播，避免其他客户端重复渲染。
            if (!Boolean.TRUE.equals(applyResult.getDuplicated())) {
                broadcastOperation(session, applyResult);
            }
        } catch (BusinessException businessException) {
            sendErrorMessage(session, businessException.getCode(), businessException.getMessage(), clientMessage.getOpId());
        } catch (Exception exception) {
            log.error("处理协同操作失败", exception);
            sendErrorMessage(session, ErrorCode.SYSTEM_ERROR.getCode(), "服务端处理失败", clientMessage.getOpId());
        }
    }

    private void sendSyncState(WebSocketSession session, Long pictureId) {
        try {
            // 新用户进入房间时，先拿快照，不需要从日志回放。
            PictureCollabStateVo state = pictureCollabService.getOrInitState(pictureId);
            PictureEditServerMessage response = new PictureEditServerMessage();
            response.setType(PictureCollabConstant.MESSAGE_SYNC_STATE);
            response.setPictureId(state.getPictureId());
            response.setServerRevision(state.getRevision());
            response.setAngle(state.getAngle());
            response.setScale(state.getScale());
            response.setCropX(state.getCropX());
            response.setCropY(state.getCropY());
            response.setCropWidth(state.getCropWidth());
            response.setCropHeight(state.getCropHeight());
            response.setServerTime(System.currentTimeMillis());
            sendServerMessage(session, response);
        } catch (BusinessException businessException) {
            sendErrorMessage(session, businessException.getCode(), businessException.getMessage(), null);
        } catch (Exception exception) {
            log.error("发送同步状态失败", exception);
            sendErrorMessage(session, ErrorCode.SYSTEM_ERROR.getCode(), "同步状态失败", null);
        }
    }

    private void sendAck(WebSocketSession session, PictureCollabApplyResult applyResult) {
        // ACK 的语义是：服务端已经确认应用（或确认它是重复操作）。
        PictureEditServerMessage ackMessage = new PictureEditServerMessage();
        ackMessage.setType(PictureCollabConstant.MESSAGE_ACK);
        ackMessage.setPictureId(applyResult.getPictureId());
        ackMessage.setUserId(applyResult.getUserId());
        ackMessage.setOpId(applyResult.getOpId());
        ackMessage.setOpType(applyResult.getOpType());
        ackMessage.setValue(applyResult.getOpValue());
        ackMessage.setBaseRevision(applyResult.getBaseRevision());
        ackMessage.setServerRevision(applyResult.getServerRevision());
        ackMessage.setAngle(applyResult.getAngle());
        ackMessage.setScale(applyResult.getScale());
        ackMessage.setCropX(applyResult.getCropX());
        ackMessage.setCropY(applyResult.getCropY());
        ackMessage.setCropWidth(applyResult.getCropWidth());
        ackMessage.setCropHeight(applyResult.getCropHeight());
        ackMessage.setServerTime(System.currentTimeMillis());
        try {
            sendServerMessage(session, ackMessage);
        } catch (IOException exception) {
            log.warn("发送 ACK 失败, sessionId={}", session.getId(), exception);
        }
    }

    private void broadcastOperation(WebSocketSession sourceSession, PictureCollabApplyResult applyResult) {
        Set<WebSocketSession> sessions = roomSessionMap.get(applyResult.getPictureId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // 广播的内容不是“原始操作意图”，而是“服务端应用后的权威状态”。
        PictureEditServerMessage broadcastMessage = new PictureEditServerMessage();
        broadcastMessage.setType(PictureCollabConstant.MESSAGE_BROADCAST_OP);
        broadcastMessage.setPictureId(applyResult.getPictureId());
        broadcastMessage.setUserId(applyResult.getUserId());
        broadcastMessage.setOpId(applyResult.getOpId());
        broadcastMessage.setOpType(applyResult.getOpType());
        broadcastMessage.setValue(applyResult.getOpValue());
        broadcastMessage.setBaseRevision(applyResult.getBaseRevision());
        broadcastMessage.setServerRevision(applyResult.getServerRevision());
        broadcastMessage.setAngle(applyResult.getAngle());
        broadcastMessage.setScale(applyResult.getScale());
        broadcastMessage.setCropX(applyResult.getCropX());
        broadcastMessage.setCropY(applyResult.getCropY());
        broadcastMessage.setCropWidth(applyResult.getCropWidth());
        broadcastMessage.setCropHeight(applyResult.getCropHeight());
        broadcastMessage.setServerTime(System.currentTimeMillis());

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                removeSession(session, false);
                continue;
            }
            // 发起操作的那个连接已经收到 ACK，这里不再重复广播给它。
            if (sourceSession.getId().equals(session.getId())) {
                continue;
            }
            try {
                sendServerMessage(session, broadcastMessage);
            } catch (IOException exception) {
                log.warn("广播协同消息失败, sessionId={}", session.getId(), exception);
                removeSession(session, false);
            }
        }
    }

    private void sendPong(WebSocketSession session) {
        PictureEditServerMessage message = new PictureEditServerMessage();
        message.setType(PictureCollabConstant.MESSAGE_PONG);
        message.setServerTime(System.currentTimeMillis());
        try {
            sendServerMessage(session, message);
        } catch (IOException exception) {
            log.warn("发送 PONG 失败, sessionId={}", session.getId(), exception);
        }
    }

    private void sendErrorMessage(WebSocketSession session, Integer code, String message, String opId) {
        PictureEditServerMessage response = new PictureEditServerMessage();
        response.setType(PictureCollabConstant.MESSAGE_ERROR);
        response.setCode(code);
        response.setMessage(message);
        response.setOpId(opId);
        response.setServerTime(System.currentTimeMillis());
        try {
            sendServerMessage(session, response);
        } catch (IOException exception) {
            log.warn("发送 ERROR 消息失败, sessionId={}", session.getId(), exception);
        }
    }

    private void sendOnlineCount(WebSocketSession session, Long pictureId) {
        if (session == null || pictureId == null || !session.isOpen()) {
            return;
        }
        PictureEditServerMessage message = buildOnlineCountMessage(pictureId);
        try {
            sendServerMessage(session, message);
        } catch (IOException exception) {
            log.warn("发送在线人数失败, sessionId={}", session.getId(), exception);
        }
    }

    private void broadcastOnlineCount(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        Set<WebSocketSession> sessions = roomSessionMap.get(pictureId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        PictureEditServerMessage message = buildOnlineCountMessage(pictureId);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                removeSession(session, false);
                continue;
            }
            try {
                sendServerMessage(session, message);
            } catch (IOException exception) {
                log.warn("广播在线人数失败, sessionId={}", session.getId(), exception);
                removeSession(session, false);
            }
        }
    }

    private PictureEditServerMessage buildOnlineCountMessage(Long pictureId) {
        PictureEditServerMessage message = new PictureEditServerMessage();
        message.setType(PictureCollabConstant.MESSAGE_ONLINE_COUNT);
        message.setPictureId(pictureId);
        message.setOnlineCount(getOnlineEditorCount(pictureId));
        message.setServerTime(System.currentTimeMillis());
        return message;
    }

    private int getOnlineEditorCount(Long pictureId) {
        Set<WebSocketSession> sessions = roomSessionMap.get(pictureId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int onlineCount = 0;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                onlineCount++;
            }
        }
        return onlineCount;
    }

    private void sendServerMessage(WebSocketSession session, PictureEditServerMessage message) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(message);
        // 同一个 session 发送消息时做串行化，避免并发写 socket。
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    private Long removeSession(WebSocketSession session, boolean removeEmptyRoom) {
        Long pictureId = sessionRoomMap.remove(session.getId());
        if (pictureId == null) {
            return null;
        }
        Set<WebSocketSession> sessions = roomSessionMap.get(pictureId);
        if (sessions != null) {
            sessions.remove(session);
            if (removeEmptyRoom && sessions.isEmpty()) {
                roomSessionMap.remove(pictureId);
            }
        }
        return pictureId;
    }

    private Long getSessionPictureId(WebSocketSession session) {
        Object pictureIdObj = session.getAttributes().get(PictureCollabConstant.WS_SESSION_PICTURE_ID);
        if (pictureIdObj instanceof Long) {
            return (Long) pictureIdObj;
        }
        if (pictureIdObj instanceof Integer) {
            return ((Integer) pictureIdObj).longValue();
        }
        if (pictureIdObj instanceof String && StrUtil.isNumeric((String) pictureIdObj)) {
            return Long.parseLong((String) pictureIdObj);
        }
        return null;
    }

    private Long getSessionUserId(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get(PictureCollabConstant.WS_SESSION_USER_ID);
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        if (userIdObj instanceof String && StrUtil.isNumeric((String) userIdObj)) {
            return Long.parseLong((String) userIdObj);
        }
        return null;
    }
}
