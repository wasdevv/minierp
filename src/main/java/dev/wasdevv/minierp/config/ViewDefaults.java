package dev.wasdevv.minierp.config;

import java.time.ZoneId;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Model attributes every page can use. */
@ControllerAdvice
public class ViewDefaults {

    private final ZoneId zone;

    public ViewDefaults(ZoneId shopZone) {
        this.zone = shopZone;
    }

    @ModelAttribute("zone")
    public ZoneId zone() {
        return zone;
    }
}
