package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.domain.Domain.TraceStep;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentStreamService {
    private final KnowledgeAgentHarness harness;
    private final Executor executor;

    public AgentStreamService(KnowledgeAgentHarness harness,
                              @Qualifier("knowledgeAgentExecutor") Executor executor) {
        this.harness = harness;
        this.executor = executor;
    }

    public SseEmitter stream(String userId, AskRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        emitter.onError(error -> cancelled.set(true));
        executor.execute(() -> {
            try {
                AskResponse response = harness.ask(userId, request, step -> sendTrace(emitter, step), cancelled::get);
                emitter.send(SseEmitter.event().name("result").data(response));
                emitter.complete();
            } catch (RunCancelledException ex) {
                sendError(emitter, "CANCELLED", "Agent run cancelled");
            } catch (ForbiddenException ex) {
                sendError(emitter, "FORBIDDEN", ex.getMessage());
            } catch (Exception ex) {
                sendError(emitter, "STREAM_FAILED", ex.getMessage() == null ? "流式执行失败" : ex.getMessage());
            }
        });
        return emitter;
    }

    private void sendTrace(SseEmitter emitter, TraceStep step) {
        try {
            emitter.send(SseEmitter.event().name("trace").data(step));
        } catch (IOException ignored) {
            // The HTTP client may disconnect before the Agent run completes.
        }
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("code", code, "message", message)));
        } catch (IOException ignored) {
        } finally {
            emitter.complete();
        }
    }
}
