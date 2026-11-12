package com.itsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One named setting an administrator can change while the application runs,
 * for example which assignment strategy is active. Stored in the database so
 * the choice survives a restart.
 */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(length = 60)
    private String name;

    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;

    /** Required by JPA. */
    protected Setting() {
    }

    public Setting(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
