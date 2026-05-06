package app.rubeton.toniq.service.megatix;

public interface MegatixAuthService {

    String getValidAccessToken();

    void evictAccessToken();
}
