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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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

    /**
     * Papéis do usuário, persistidos em {@code usuario_papeis}. Carregados junto com o usuário
     * porque a autenticação precisa deles a cada requisição para montar as authorities.
     */
    @NotEmpty(message = "O usuário deve ter ao menos um papel")
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 30)
    private Set<PapelUsuario> papeis = EnumSet.noneOf(PapelUsuario.class);

    /** Preenchidos pelo Hibernate: o DEFAULT do banco só cobriria o INSERT. */
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "modificado_em", nullable = false)
    private Instant modificadoEm;

    public Usuario(String nome, String cpf, String email, String senhaHash, PapelUsuario papel) {
        this(nome, cpf, email, senhaHash, EnumSet.of(papel));
    }

    public Usuario(
            String nome, String cpf, String email, String senhaHash, Set<PapelUsuario> papeis) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papeis =
                papeis.isEmpty() ? EnumSet.noneOf(PapelUsuario.class) : EnumSet.copyOf(papeis);
    }

    public boolean temPapel(PapelUsuario papel) {
        return papeis.contains(papel);
    }

    public void adicionarPapel(PapelUsuario papel) {
        papeis.add(papel);
    }

    public Set<PapelUsuario> getPapeis() {
        return Collections.unmodifiableSet(papeis);
    }
}
