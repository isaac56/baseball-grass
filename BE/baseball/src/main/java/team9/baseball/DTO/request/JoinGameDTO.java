package team9.baseball.DTO.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import team9.baseball.domain.enums.Venue;

import javax.validation.constraints.NotNull;

@Getter
@AllArgsConstructor
public class JoinGameDTO {
    private int game_id;
    
    @NotNull
    private Venue my_venue;
}
