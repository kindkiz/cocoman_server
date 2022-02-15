package orangetaxiteam.cocoman.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import orangetaxiteam.cocoman.application.dto.UserCreateRequestDTO;
import orangetaxiteam.cocoman.application.dto.UserDTO;
import orangetaxiteam.cocoman.application.dto.UserSignInDTO;
import orangetaxiteam.cocoman.application.dto.UserUpdateRequestDTO;
import orangetaxiteam.cocoman.application.dto.StarRatingDTO;
import orangetaxiteam.cocoman.config.JwtTokenProvider;
import orangetaxiteam.cocoman.domain.*;
import orangetaxiteam.cocoman.domain.exceptions.BadRequestException;
import orangetaxiteam.cocoman.domain.exceptions.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserApplicationService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final StarRatingRepository starRatingRepository;
    private final PasswordValidator passwordValidator;
    private final SocialInfoServiceSupplier socialInfoServiceSupplier;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserApplicationService(
            UserRepository userRepository,
            StarRatingRepository starRatingRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordValidator passwordValidator,
            SocialInfoServiceSupplier socialInfoServiceSupplier,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.starRatingRepository = starRatingRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordValidator = passwordValidator;
        this.socialInfoServiceSupplier = socialInfoServiceSupplier;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDTO create(UserCreateRequestDTO userCreateRequestDTO) {
        SocialProvider provider = userCreateRequestDTO.getProvider();

        if (provider == SocialProvider.COCONUT) {
            this.validateUserId(userCreateRequestDTO.getUserId());
            User user = User.of(
                    userCreateRequestDTO.getUserId(),
                    userCreateRequestDTO.getNickName(),
                    passwordEncoder.encode(userCreateRequestDTO.getPassword()),
                    userCreateRequestDTO.getAge(),
                    userCreateRequestDTO.getGender(),
                    userCreateRequestDTO.getPhoneNum(),
                    userCreateRequestDTO.getProfileImg(),
                    userCreateRequestDTO.getPushToken()
            );
            return UserDTO.from(this.userRepository.save(user));
        }

        SocialInfoService socialInfoService = this.socialInfoServiceSupplier.supply(provider);
        String socialId = socialInfoService.getSocialId(userCreateRequestDTO.getAccessToken());

        User user = User.social(
                socialId,
                userCreateRequestDTO.getNickName(),
                userCreateRequestDTO.getAge(),
                userCreateRequestDTO.getGender(),
                userCreateRequestDTO.getPhoneNum(),
                userCreateRequestDTO.getProfileImg(),
                userCreateRequestDTO.getPushToken()
        );
        return UserDTO.from(this.userRepository.save(user));

    }

    public UserDTO signIn(UserSignInDTO userSignInDTO) {
        SocialProvider provider = userSignInDTO.getProvider();

        if (provider == SocialProvider.COCONUT) {
            User user = this.userRepository.findByUserId(userSignInDTO.getUserId()).orElseThrow(
                    () -> new BadRequestException(ErrorCode.SIGNIN_DATA_DOES_NOT_MATCH, "invalid user data")
            );
            this.passwordValidator.validate(
                    user,
                    userSignInDTO.getPassword()
            );

            return UserDTO.from(
                    user,
                    this.jwtTokenProvider.createToken(user.getId())
            );
        }

        SocialInfoService socialInfoService = this.socialInfoServiceSupplier.supply(provider);
        String socialId = socialInfoService.getSocialId(userSignInDTO.getAccessToken());

        User user = this.userRepository.findByUserId(socialId).orElseThrow(
                () -> new BadRequestException(ErrorCode.SIGNIN_DATA_DOES_NOT_MATCH, "invalid user data")
        );

        return UserDTO.from(
                user,
                this.jwtTokenProvider.createToken(user.getId())
        );
    }

    public UserDTO findById(String id) {
        return UserDTO.from(this.userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(ErrorCode.ROW_DOES_NOT_EXIST, "Invalid user id")));
    }

    public UserDTO updateUser(String id, UserUpdateRequestDTO userUpdateRequestDTO) {
        User user = this.userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(ErrorCode.ROW_DOES_NOT_EXIST, "Invalid user id"));
        user.update(
                userUpdateRequestDTO.getNickName(),
                userUpdateRequestDTO.getAge(),
                userUpdateRequestDTO.getGender(),
                userUpdateRequestDTO.getPhoneNum(),
                userUpdateRequestDTO.getProfileImg()
        );
        return UserDTO.from(this.userRepository.save(user));
    }

    public void deleteUser(String id) {
        User user = this.userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException(ErrorCode.ROW_DOES_NOT_EXIST, "Invalid user id"));
        this.userRepository.delete(user);
    }

    public void validateUserId(String userId) {
        if (this.userRepository.existsByUserId(userId)) {
            throw new BadRequestException(ErrorCode.ID_ALREADY_EXIST, "user id already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<StarRatingDTO> findStarRatingByUserId(Pageable pageable, String userId){
        return this.starRatingRepository.findByUserId(pageable, userId)
                .stream()
                .map(StarRatingDTO::from)
                .collect(Collectors.toList());
    }
}
