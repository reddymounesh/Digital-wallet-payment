import { useState } from "react";
import InputField from "../components/common/InputField";
import Button from "../components/common/Button";
import AuthService from "../services/AuthService";

function LoginPage() {

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const handleLogin = async (e) => {

        e.preventDefault();

        try {

            const data = await AuthService.login(email, password);

            console.log(data);

            localStorage.setItem("jwt", data.token);

            alert("Login Successful");

        } catch (error) {

            console.error(error);

            alert("Invalid Credentials");

        }

    };

    return (

        <div>

            <h1>Wallet App</h1>

            <form onSubmit={handleLogin}>

                <InputField
                    type="email"
                    placeholder="Enter Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />

                <br /><br />

                <InputField
                    type="password"
                    placeholder="Enter Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />

                <br /><br />

                <Button text="Login" />

            </form>

        </div>

    );
}

export default LoginPage;