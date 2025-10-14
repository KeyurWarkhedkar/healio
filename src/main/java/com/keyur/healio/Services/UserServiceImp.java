package com.keyur.healio.Services;

import com.keyur.healio.Entities.User;
import com.keyur.healio.Repositories.UserRepository;

public class UserServiceImp implements UserService {
    //fields
    UserRepository userRepository;

    //injecting using dependency injection
    public UserServiceImp(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //method to add a new user to the database
    public User addUser(User newUser) {

    }
}
