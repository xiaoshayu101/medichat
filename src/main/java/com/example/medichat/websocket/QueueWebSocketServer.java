package com.example.medichat.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/queue/{role}/{id}")
public class QueueWebSocketServer {

    // 存所有"患者"连接：key=appointmentId, value=连接会话
    private static final ConcurrentHashMap<String, Session> PATIENT_SESSIONS = new ConcurrentHashMap<>();

    // 存所有"大屏"连接：key=doctorId, value=连接会话
    private static final ConcurrentHashMap<String, Session> SCREEN_SESSIONS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Session> DOCTOR_SESSIONS = new ConcurrentHashMap<>();

    private String role;
    private String id;

    @OnOpen
    public void onOpen(Session session, @PathParam("role") String role, @PathParam("id") String id) {
        this.role = role;
        this.id = id;

        if ("patient".equals(role)) {
            PATIENT_SESSIONS.put(id, session);
        } else if ("screen".equals(role)) {
            SCREEN_SESSIONS.put(id, session);
        }else if ("doctor".equals(role)) {
            DOCTOR_SESSIONS.put(id, session);
        }
    }
//连接断开（关闭网页/断网）时执行，清理缓存
    @OnClose
    public void onClose() {
        if ("patient".equals(role)) {
            PATIENT_SESSIONS.remove(id);
        } else if ("screen".equals(role)) {
            SCREEN_SESSIONS.remove(id);
        }else if ("doctor".equals(role)) {
            DOCTOR_SESSIONS.remove(id);
        }
    }
//接收前端发送来的数据。心跳包用于保持连接不被服务端超时自动关闭
    @OnMessage
    public void onMessage(String message, Session session) {
        // 心跳包处理：患者端每30秒发个ping，这里不用特殊处理，单纯保活
    }
//当网络不稳定发生异常时捕获报错，防止系统崩溃。
    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    /**
     * 给指定患者推送消息
     * @return true=推送成功（患者在线），false=患者不在线
     */
    public static boolean sendToPatient(String appointmentId, String message) {
        Session session = PATIENT_SESSIONS.get(appointmentId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    /**
     * 给指定医生的大屏广播消息
     */
    public static void sendToScreen(String doctorId, String message) {
        Session session = SCREEN_SESSIONS.get(doctorId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
//    推送给医生
    public static void sendToDoctor(String doctorId, String message) {
        Session session = DOCTOR_SESSIONS.get(doctorId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
