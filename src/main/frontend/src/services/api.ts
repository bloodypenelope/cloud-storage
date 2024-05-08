import axios, { AxiosError } from "axios";
import createAuthRefreshInterceptor from "axios-auth-refresh";
import TokenService from "./token-service";

const instance = axios.create({
  baseURL: "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
});

const refreshAuthLogic = async (failedRequest: AxiosError) => {
  const response = await instance.post<RefreshTokenResponse>(
    "/api/auth/refresh",
    {
      token: TokenService.getLocalRefreshToken(),
    }
  );
  const type = response.data.type;
  const token = response.data.accessToken;
  TokenService.updateLocalAccessToken(token);

  if (failedRequest.response)
    failedRequest.response.config.headers["Authorization"] = `${type} ${token}`;
  return Promise.resolve();
};

instance.interceptors.request.use((request) => {
  const type = TokenService.getTokenType();
  const token = TokenService.getLocalAccessToken();

  request.headers["Authorization"] = `${type} ${token}`;
  return request;
});

createAuthRefreshInterceptor(instance, (failedRequest: AxiosError) =>
  refreshAuthLogic(failedRequest).catch((error: AxiosError) => {
    if (error.response?.status === 403) throw error;
  })
);

export default instance;