package com.asu.secureBankApp.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.asu.secureBankApp.security.CustomAuthenticationProvider;
import com.asu.secureBankApp.security.MyUserDetailsService;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private MyUserDetailsService userDetailsService;

	final CustomAuthenticationProvider authProvider = new CustomAuthenticationProvider();
    
    @Bean("authenticationManager")
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception 
    {
        http
    		.authorizeRequests()
			.antMatchers("/").permitAll()
			.antMatchers("/index.html").permitAll()
            .antMatchers("/api/user/signup*").permitAll()
            .antMatchers("/api/user/doesUsernameExist/*").permitAll()
			.antMatchers("/api/accountRequest/list").hasAnyAuthority("AUTHORIZE_CRITICAL_TRANSACTIONS")
            .antMatchers("/api/cheque/issue*").hasAnyAuthority("CREATE_ACCOUNT_REQUEST")
			.antMatchers("/api/cheque/listIssueApprovals").hasAnyAuthority("AUTHORIZE_NON_CRITICAL_TRANSACTIONS")
			.antMatchers("/api/cheque/approve/*").hasAnyAuthority("AUTHORIZE_NON_CRITICAL_TRANSACTIONS")
			.antMatchers("/api/cheque/reject/*").hasAnyAuthority("AUTHORIZE_NON_CRITICAL_TRANSACTIONS")
			.antMatchers("/api/cheque/listAvailableCheques/*").hasAnyAuthority("AUTHORIZE_NON_CRITICAL_TRANSACTIONS")
			.antMatchers("/api/cheque/depositCheque*").hasAnyAuthority("CREATE_ACCOUNT_REQUEST")
			.antMatchers("/api/log/*").hasAnyAuthority("VIEW_SYSTEM_LOG")
			.antMatchers("/api/generateOtp/*").permitAll()
			.antMatchers("/api/verifyOtp*").permitAll()
			.antMatchers("/api/login").permitAll()
			.antMatchers("/api/transaction/balance", "/api/transaction/transfer", "/api/transaction/get*").hasAnyAuthority("AUTHORIZE_CUSTOMER_TRANSFER_REQUEST")
			.antMatchers("/api/transaction/approve/*","/api/transaction/reject/*").hasAnyAuthority("AUTHORIZE_CRITICAL_TRANSACTIONS")
			.antMatchers("/api/**").hasAnyAuthority("AUTHORIZE_CUSTOMER_TRANSFER_REQUEST")
			.anyRequest().authenticated()
	    .and()
	    .csrf().disable()
		.logout()
			.logoutUrl("/api/logout")
			.invalidateHttpSession(true)
			.deleteCookies("JSESSIONID")
			.logoutSuccessUrl("/index.html").and()
		.exceptionHandling()
			.accessDeniedPage("/access-denied");

		http.sessionManagement()
		.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
		.maximumSessions(1)
		;
	}

	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	@Override
	protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
		auth.authenticationProvider(authProvider());
	}

	@Bean
	public DaoAuthenticationProvider authProvider() {
		final CustomAuthenticationProvider authProvider = new CustomAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	public PasswordEncoder encoder() {
		return new BCryptPasswordEncoder(11);
		// return new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-256");
		// return new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("MD5");
	}

	@SuppressWarnings("deprecation")
	@Bean
	public static NoOpPasswordEncoder passwordEncoder() {
		return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
	}

	@Override
	public void configure(WebSecurity web) throws Exception {
		web.ignoring().antMatchers("/modules/**", "/resources/**", "/scripts/**", "/css/**", "/js/**", "/images/**")
				.antMatchers(HttpMethod.POST, "/login");
		;
	}
}