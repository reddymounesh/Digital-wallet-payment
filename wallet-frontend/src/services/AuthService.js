import axiosClient from "../api/axiosClient";

const AuthService = {

    login: async (email, password) => {

        const response = await axiosClient.post("/auth/login", {
            email,
            password,
        });

        return response.data;
    },

    register: async (user) => {

        const response = await axiosClient.post("/auth/register", user);

        return response.data;
    },

    logout: () => {

        localStorage.removeItem("jwt");

    }

};

export default AuthService;