package com.unicamp.engsoft.eleicao.usuario.domain;

@Entity
@Table(name = "usuarios")
@Getter
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

    @NotBlank(message = "A senha é obrigatória")
    @Column(nullable = false)
    private String senha;

    @NotNull(message = "O papel do usuário é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelUsuario papel;

    public Usuario(String nome, String cpf, String email, String senha, PapelUsuario papel) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.senha = senha; // ela é criptofrafada
        this.papel = papel;
    }
}
