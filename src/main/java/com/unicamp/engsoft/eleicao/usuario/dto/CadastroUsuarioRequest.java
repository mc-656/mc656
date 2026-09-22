package com.unicamp.engsoft.eleicao.usuario.dto;

import com.unicamp.engsoft.eleicao.usuario.domain.PapelUsuario;
import com.unicamp.engsoft.eleicao.usuario.domain.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

/**
 * Dados aceitos no cadastro (RF-01).
 *
 * <p>Deliberadamente não expõe {@code id} nem {@code papel}: ambos são definidos pelo servidor.
 */
public record CadastroUsuarioRequest(
        @NotBlank(message = "O nome é obrigatório")
                @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
                String nome,
        @NotBlank(message = "O CPF é obrigatório") @CPF(message = "O CPF deve ser válido")
                String cpf,
        @NotBlank(message = "O email é obrigatório")
                @Email(message = "O email deve ser válido")
                @Size(max = 255, message = "O email deve ter no máximo 255 caracteres")
                String email,
        @NotBlank(message = "A senha é obrigatória")
                @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
                String senha) {

    public Usuario paraUsuario(String senhaHash) {
        return new Usuario(nome, cpf, email, senhaHash, PapelUsuario.ELEITOR);
    }
}
