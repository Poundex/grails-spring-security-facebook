/* Copyright 2006-2010 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
import com.the6hours.grails.springsecurity.facebook.FacebookAuthProvider
import com.the6hours.grails.springsecurity.facebook.FacebookAuthDirectFilter
import com.the6hours.grails.springsecurity.facebook.FacebookAuthCookieTransparentFilter
import com.the6hours.grails.springsecurity.facebook.FacebookAuthUtils
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.codehaus.groovy.grails.plugins.springsecurity.SecurityFilterPosition
import com.the6hours.grails.springsecurity.facebook.FacebookAuthCookieLogoutHandler
import com.the6hours.grails.springsecurity.facebook.DefaultFacebookAuthDao
import com.the6hours.grails.springsecurity.facebook.FacebookAuthCookieDirectFilter

class SpringSecurityFacebookGrailsPlugin {

   String version = '0.8'
   String grailsVersion = '2.0.0 > *'
   Map dependsOn = ['springSecurityCore': '1.2.7.2 > *']

   def license = 'APACHE'

   def developers = [
       //extra developers
   ]
   def issueManagement = [ system: "GitHub", url: "https://github.com/splix/grails-spring-security-facebook/issues" ]
   def scm = [ url: "git@github.com:splix/grails-spring-security-facebook.git" ]

   String author = 'Igor Artamonov'
   String authorEmail = 'igor@artamonov.ru'
   String title = 'Facebook Authentication'
   String description = 'Facebook Connect authentication support for the Spring Security plugin.'

   String documentation = 'http://grails.org/plugin/spring-security-facebook'

   def doWithSpring = {

       def conf = SpringSecurityUtils.securityConfig
       if (!conf) {
           println 'ERROR: There is no Spring Security configuration'
           println 'ERROR: Stop configuring Spring Security Facebook'
           return
       }

	   println 'Configuring Spring Security Facebook ...'
	   SpringSecurityUtils.loadSecondaryConfig 'DefaultFacebookSecurityConfig'
	   // have to get again after overlaying DefaultFacebookecurityConfig
	   conf = SpringSecurityUtils.securityConfig

       if (!conf.facebook.bean.dao) {
           conf.facebook.bean.dao = 'facebookAuthDao'
           facebookAuthDao(DefaultFacebookAuthDao) {
               domainClassName = conf.facebook.domain.classname
               connectionPropertyName = conf.facebook.domain.connectionPropertyName
               userDomainClassName = conf.userLookup.userDomainClassName
               rolesPropertyName = conf.userLookup.authoritiesPropertyName
           }
       }

       facebookAuthUtils(FacebookAuthUtils) {
           apiKey = conf.facebook.apiKey
           secret = conf.facebook.secret
           applicationId = conf.facebook.appId
       }

       SpringSecurityUtils.registerProvider 'facebookAuthProvider'
	   facebookAuthProvider(FacebookAuthProvider) {
           facebookAuthDao = ref(conf.facebook.bean.dao)
           facebookAuthUtils = ref('facebookAuthUtils')
	   }

       addFilters(conf, delegate)
   }

   private void addFilters(def conf, def delegate) {
       def typesRaw = conf.facebook.filter.types
       List<String> types = null
       if (!typesRaw) {
           typesRaw = conf.facebook.filter.type
       }

       String defaultType = 'transparent'
       List validTypes = ['transparent', 'cookieDirect']

       if (!typesRaw) {
           log.error("Invalid Facebook Authentication filters configuration: '$typesRaw'. Should be used on of: $validTypes. Current value will be ignored, and type '$defaultType' will be used instead.")
           types = [defaultType]
       } else if (typesRaw instanceof Collection) {
           types = typesRaw.collect { it.toString() }.findAll { it in validTypes }
       } else if (typesRaw instanceof String) {
           types = typesRaw.split(',').collect { it.trim() }.findAll { it in validTypes }
       }

       if (!types || types.empty) {
           log.error("Facebook Authentication filter is not configured. Should be used on of: $validTypes, and '$defaultType' will be used by default.")
           log.error("To configure Facebook Authentication filters you should add to Config.groovy:")
           log.error("grails.plugins.springsecurity.facebook.filter.types='transparent'")
           log.error("or")
           log.error("grails.plugins.springsecurity.facebook.filter.types='transparent,cookieDirect'")

           types = [defaultType]
       }

       int basePosition = conf.facebook.filter.position

       addFilter.delegate = delegate
       types.eachWithIndex { name, idx ->
           addFilter(conf, name, basePosition + 1 + idx)
       }
   }

   private addFilter = { def conf, String name, int position ->
       if (name == 'transparent') {
           SpringSecurityUtils.registerFilter 'facebookAuthCookieTransparentFilter', position
           facebookAuthCookieTransparentFilter(FacebookAuthCookieTransparentFilter) {
               authenticationManager = ref('authenticationManager')
               facebookAuthUtils = ref('facebookAuthUtils')
               logoutUrl = conf.logout.filterProcessesUrl
               forceLoginParameter = conf.facebook.filter.forceLoginParameter
           }
           facebookAuthCookieLogout(FacebookAuthCookieLogoutHandler) {
               facebookAuthUtils = ref('facebookAuthUtils')
           }
           SpringSecurityUtils.registerLogoutHandler('facebookAuthCookieLogout')
       } else if (name == 'cookieDirect') {
           SpringSecurityUtils.registerFilter 'facebookAuthCookieDirectFilter', position
           facebookAuthCookieDirectFilter(FacebookAuthCookieDirectFilter, conf.facebook.filter.processUrl) {
               authenticationManager = ref('authenticationManager')
               facebookAuthUtils = ref('facebookAuthUtils')
           }
       } else {
           log.error("Invalid filter type: $name")
       }
   }

   def doWithApplicationContext = { ctx ->
   }
}
