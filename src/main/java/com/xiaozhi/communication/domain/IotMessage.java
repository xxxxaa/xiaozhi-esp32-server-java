package com.xiaozhi.communication.domain;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class IotMessage extends Message {
    public IotMessage() {
        super("iot");
    }

    private boolean update;
    private String sessionId;
    private List<IotState> states;
    private List<IotDescriptor> descriptors;

    @Data
    public static class IotState {
        private String name;
        private Map<String, Object> state;
    }
}
