package ru.yandex.practicum.telemetry.analyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;

@Entity
@Table(name = "actions")
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionTypeAvro type;

    @Column(name = "\"value\"")
    private Integer value;

    public Action() {
    }

    public Action(ActionTypeAvro type, Integer value) {
        this.type = type;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public ActionTypeAvro getType() {
        return type;
    }

    public void setType(ActionTypeAvro type) {
        this.type = type;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
