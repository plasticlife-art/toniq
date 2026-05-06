package app.rubeton.toniq.service.megatix.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MegatixLoginRequestDto {

    private String email;
    private String password;
}
