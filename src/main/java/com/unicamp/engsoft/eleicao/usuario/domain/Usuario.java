package com.unicamp.engsoft.eleicao.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.br.CPF;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "O nome é obrigatório")
    @Column(nullable = false)
    private String nome;

    @NotBlank(message = "O CPF é obrigatório")
    @CPF(message = "O CPF deve ser válido")
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @NotBlank(message = "O email é obrigatório")
    @Column(nullable = false, unique = true)
    @Email(message = "O email deve ser válido")
    private String email;

    /** Apenas o hash BCrypt da senha. Senha em texto plano nunca chega até aqui. */
    @NotBlank(message = "A senha é obrigatória")
    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @NotNull(message = "O papel é obrigatório")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "papel", nullable = false, columnDefinition = "papel_usuario")
    private PapelUsuario papel;

    /** Preenchidos pelo Hibernate: o DEFAULT do banco só cobriria o INSERT. */
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "modificado_em", nullable = false)
    private Instant modificadoEm;

    public Usuario(String nome, String cpf, String email, String senhaHash, PapelUsuario papel) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    public boolean temPapel(PapelUsuario papel) {
        return this.papel == papel;
    }
}
