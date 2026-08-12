package com.stark.wallet.wallet_app.service;

import com.stark.wallet.wallet_app.entity.User;
import com.stark.wallet.wallet_app.repository.UserRepository;
import com.stark.wallet.wallet_app.security.CustomUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository){
        this.userRepository=userRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException{
        User user = userRepository.findByEmail(email).orElseThrow(() ->new UsernameNotFoundException("User not found:"+email));
        return new CustomUserDetails(user);
    }

}
