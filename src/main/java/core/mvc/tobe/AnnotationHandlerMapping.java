package core.mvc.tobe;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import core.annotation.web.RequestMapping;
import core.annotation.web.RequestMethod;
import core.mvc.ModelAndView;
import core.mvc.tobe.argumentresolver.NumberArgumentResolver;
import core.mvc.tobe.argumentresolver.HandlerMethodArgumentResolver;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnnotationHandlerMapping implements HandlerMapping {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationHandlerMapping.class);
    private static final String METHOD_INFO_FORMAT = "%s.%s";
    private static final String MAPPING_INFO_LOG_FORMAT = "{}, execution method: {}";

    private Object[] basePackage;
    private Map<HandlerKey, HandlerExecution> handlerExecutions = Maps.newHashMap();
    private List<HandlerMethodArgumentResolver> handlerMethodArgumentResolvers = Lists.newArrayList();

    public AnnotationHandlerMapping(Object... basePackage) {
        this.basePackage = basePackage;
    }

    public void initialize() {
        ControllerScanner controllerScanner = new ControllerScanner(basePackage);
        initHandlerExecution(controllerScanner.getInstantiateControllerMap());
        handlerMethodArgumentResolvers.add(new NumberArgumentResolver());
    }

    private void initHandlerExecution(Map<Class<?>, Object> instantiateControllerMap) {
        for (Class<?> aClass : instantiateControllerMap.keySet()) {
            Object controller = instantiateControllerMap.get(aClass);
            addExecutionOfController(controller, aClass);
        }
    }

    private void addExecutionOfController(Object controller, Class<?> aClass) {
        Class<RequestMapping> requestMappingClass = RequestMapping.class;
        Set<Method> methods = ReflectionUtils.getAllMethods(aClass, ReflectionUtils.withAnnotation(requestMappingClass));

        for (Method method : methods) {
            HandlerKey handlerKey = createHandlerKey(method.getAnnotation(requestMappingClass));
            HandlerExecution handlerExecution = getHandlerExecution(controller, method);
            handlerExecutions.put(handlerKey, handlerExecution);
            loggingMappingInfo(handlerKey, method);
        }
    }

    private HandlerExecution getHandlerExecution(Object controller, Method method) {
        return (request, response) -> {
            return (ModelAndView) method.invoke(controller, request, response);
        };
    }

    private HandlerKey createHandlerKey(RequestMapping requestMapping) {
        return new HandlerKey(requestMapping.value(), requestMapping.method());
    }

    private void loggingMappingInfo(HandlerKey handlerKey, Method method) {
        String methodInfo = String.format(METHOD_INFO_FORMAT, method.getDeclaringClass().getName(), method.getName());
        logger.info(MAPPING_INFO_LOG_FORMAT, handlerKey, methodInfo);
    }

    public Set<HandlerKey> getHandlerKeys() {
        return this.handlerExecutions.keySet();
    }

    public boolean isHandlerKeyPresent(String url, RequestMethod method) {
        return this.handlerExecutions.containsKey(new HandlerKey(url, method));
    }

    @Override
    public HandlerExecution getHandler(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        RequestMethod requestMethod = RequestMethod.valueOf(request.getMethod().toUpperCase());
        return handlerExecutions.get(new HandlerKey(requestUri, requestMethod));
    }
}
