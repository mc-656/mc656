package com.unicamp.engsoft.eleicao.usuario.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "usuarios",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_usuarios_username", columnNames = "username"),
            @UniqueConstraint(name = "uk_usuarios_email", columnNames = "email")
        })
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @ElementCollection
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "papel", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<PapelUsuario> papeis = new HashSet<>();

    protected Usuario() {}

    public Usuario(
            String username,
            String nome,
            String email,
            String senhaHash,
            Set<PapelUsuario> papeis) {
        this.username = username;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papeis = new HashSet<>(papeis);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Set<PapelUsuario> getPapeis() {
        return papeis;
    }
}