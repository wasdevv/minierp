package dev.wasdevv.minierp.config;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Model attributes every page can use. */
@ControllerAdvice
public class ViewDefaults {

    private final ZoneId zone;

    public ViewDefaults(@Value("${minierp.time-zone:America/Sao_Paulo}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    /** Dates are stored as instants and shown in the shop's zone, whatever zone the server runs in. */
    @ModelAttribute("zone")
    public ZoneId zone() {
        return zone;
    }
}
