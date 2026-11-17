import { useEffect, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { AuthContext } from "../../context/AuthContext";

export default function OAuthCallback() {
  const { login } = useContext(AuthContext);
  const navigate = useNavigate();

  useEffect(() => {
    // token is delivered in the URL fragment; the query-string form is kept only for older deployments
    const fromHash = new URLSearchParams(window.location.hash.replace(/^#/, "")).get("token");
    const token = fromHash || new URLSearchParams(window.location.search).get("token");
    if (token) {
      // remove the token from the address bar / history immediately
      window.history.replaceState(null, "", window.location.pathname);
      login(token);
      navigate("/dashboard");
    } else {
      navigate("/login");
    }
  }, []);

  return <p>Signing you in…</p>;
}