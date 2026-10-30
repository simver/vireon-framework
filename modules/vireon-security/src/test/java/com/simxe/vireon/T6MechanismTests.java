package com.simxe.vireon;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class T6MechanismTests {@Test void browserMechanismIsAvailable(){assertThatCode(()->Class.forName("com.simxe.vireon.security.web.BrowserSecurityConfigurer")).doesNotThrowAnyException();}}
