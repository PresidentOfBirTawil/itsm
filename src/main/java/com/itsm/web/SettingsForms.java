package com.itsm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The two small forms on the settings page. */
public final class SettingsForms {

    private SettingsForms() {
    }

    /** Which assignment strategy to use. */
    public static class Strategy {

        @NotBlank(message = "Please choose a strategy")
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    /** Whether the demo SLA deadlines are on. */
    public static class DemoSla {

        @NotNull(message = "Please say whether demo SLA should be on or off")
        private Boolean enabled;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
