package com.autoparts_maven.autoparts_maven.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    // Finalidade da persistencia: guardar os dados tecnicos do veiculo consultado por
    // placa para (1) associar o veiculo a produtos compativeis (N:N) e (2) evitar
    // reconsultar o provedor externo. A placa e um dado pessoal indireto (LGPD): so e
    // armazenada com essa finalidade, sem nome/CPF/endereco nem dados do proprietario.
    // Campos sem uso em regras de compatibilidade (ex.: color) nao sao persistidos.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String plate;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 150)
    private String model;

    @Column(length = 150)
    private String version;

    @Column(name = "manufacture_year", nullable = false)
    private Integer manufactureYear;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(length = 50)
    private String fuel;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(length = 50)
    private String species;

    @Column(name = "passenger_capacity")
    private Integer passengerCapacity;

    @Column(name = "engine_cc")
    private Integer engineCc;

    @Column(length = 50)
    private String origin;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
