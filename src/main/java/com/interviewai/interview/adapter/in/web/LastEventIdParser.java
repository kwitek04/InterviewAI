package com.interviewai.interview.adapter.in.web;

import com.interviewai.interview.application.InvalidLastEventIdException;

final class LastEventIdParser {

    private LastEventIdParser() {
    }

    static int parse(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }

        try {
            int value = Integer.parseInt(lastEventId.strip());
            if (value < 0) {
                throw new InvalidLastEventIdException(lastEventId);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new InvalidLastEventIdException(lastEventId);
        }
    }
}
