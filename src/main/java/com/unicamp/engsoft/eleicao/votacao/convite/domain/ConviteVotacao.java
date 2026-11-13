package com.unicamp.engsoft.eleicao.votacao.convite.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Convite de uma votação (RF-17): o token do link/QR Code, o modo de acesso e a lista de e-mails
 * convidados.
 *
 * <p>Um por votação, com a votação como chave: o link é "da votação", então trocar o modo não pode
 * invalidar o que já circulou no WhatsApp. Pelo mesmo motivo o token nunca muda depois de gerado.
 *
 * <p>O token é a única credencial do link, por isso vem de {@link SecureRandom} com 256 bits — não
 * um UUID nem o id da votação, que são previsíveis ou já aparecem em outras URLs da API.
 */
@Entity
@Table(name = "convites_votacao")
public class ConviteVotacao {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES_DO_TOKEN = 32;

    @Id
    @Column(name = "votacao_id")
    private UUID votacaoId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModoConvite modo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @ElementCollection
    @CollectionTable(name = "convites_emails", joinColumns = @JoinColumn(name = "votacao_id"))
    @Column(name = "email", nullable = false, length = 255)
    private Set<String> emails = new HashSet<>();

    protected ConviteVotacao() {}

    public ConviteVotacao(UUID votacaoId, ModoConvite modo, Instant criadoEm) {
        this.votacaoId = votacaoId;
        this.modo = modo;
        this.criadoEm = criadoEm;
        this.token = novoToken();
    }

    /** 32 bytes aleatórios em Base64 URL-safe sem padding: 43 caracteres que cabem num path. */
    static String novoToken() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Forma canônica de um e-mail para a lista: sem espaços e em minúsculas. O cadastro não
     * normaliza o e-mail, então a comparação precisa ser feita sempre nesta forma, dos dois lados.
     */
    public static String normalizar(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void alterarModo(ModoConvite modo) {
        this.modo = modo;
    }

    public void adicionarEmails(Collection<String> novos) {
        novos.stream().map(ConviteVotacao::normalizar).forEach(emails::add);
    }

    /** Regra do modo: aberto admite qualquer um; restrito, só quem está na lista. */
    public boolean admite(String emailDoUsuario) {
        return modo == ModoConvite.ABERTO || emails.contains(normalizar(emailDoUsuario));
    }

    public UUID getVotacaoId() {
        return votacaoId;
    }

    public String getToken() {
        return token;
    }

    public ModoConvite getModo() {
        return modo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Set<String> getEmails() {
        return Collections.unmodifiableSet(emails);
    }
}
