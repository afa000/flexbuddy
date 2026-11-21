package com.angel.flexbuddy.api;

import java.util.List;

import com.angel.flexbuddy.dto.StandingEntryResponse;
import com.angel.flexbuddy.dto.StandingEventResponse;
import com.angel.flexbuddy.dto.StandingResponse;

/** Logged Flex standing and related block events for the native app (FB-057). */
record ApiStanding(String from, String to, Entry current, List<Entry> entries, List<Event> events) {

    static ApiStanding of(StandingResponse standing) {
        return new ApiStanding(ApiValues.date(standing.from()), ApiValues.date(standing.to()),
                Entry.of(standing.current()), standing.entries().stream().map(Entry::of).toList(),
                standing.events().stream().map(Event::of).toList());
    }

    record Entry(String recordedOn, String level, String note) {
        static Entry of(StandingEntryResponse entry) {
            return entry == null ? null
                    : new Entry(ApiValues.date(entry.recordedOn()), ApiValues.code(entry.level()), entry.note());
        }
    }

    record Event(String date, String startTime, String station, String kind) {
        static Event of(StandingEventResponse event) {
            return new Event(ApiValues.date(event.date()), ApiValues.time(event.startTime()),
                    event.station(), ApiValues.code(event.kind()));
        }
    }
}
