package nl.tue.win.jhalstead;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.util.*;

public class HalsteadMetricsCalculator {
    public List<HalsteadMetrics> analyzeProject(Launcher launcher, CtModel model) {

        List<HalsteadMetrics> results = new ArrayList<>();

        Map<String, List<CtClass<?>>> packageClassMap = new HashMap<>();
        for (CtType<?> ctType : model.getAllTypes()) {
            if (ctType instanceof CtClass<?> ctClass) {
                var pkg = ctClass.getPackage();
                if (pkg == null) {
                    pkg = launcher.getFactory().Package().getRootPackage();
                }
                String packageName = pkg.getQualifiedName();
                packageClassMap.computeIfAbsent(packageName, k -> new ArrayList<>()).add(ctClass);
            }
        }

        for (var entry : packageClassMap.entrySet()) {
            String pkgName = entry.getKey().isEmpty() ? "<default>" : entry.getKey();
            var pkgAggregate = new ArrayList<HalsteadMetrics>();

            for (CtClass<?> ctClass : entry.getValue()) {
                var classAggregate = new ArrayList<HalsteadMetrics>();

                for (CtMethod<?> method : ctClass.getMethods()) {
                    var result = gatherOperatorsAndOperands(method);

                    String opId = "%s#%s".formatted(method.getDeclaringType().getQualifiedName(), method.getSignature());
                    HalsteadMetrics methodMetrics = new HalsteadMetrics(
                            opId,
                            "Operation",
                            result.operators.size(),
                            result.operands.size(),
                            result.totalOperators,
                            result.totalOperands
                    );
                    classAggregate.add(methodMetrics);
                    results.add(methodMetrics);
                }

                HalsteadMetrics classMetrics = HalsteadMetrics.aggregate(
                        ctClass.getQualifiedName(),
                        "Type",
                        classAggregate
                );
                pkgAggregate.add(classMetrics);
                results.add(classMetrics);
            }

            HalsteadMetrics packageMetrics = HalsteadMetrics.aggregate(
                    pkgName,
                    "Scope",
                    pkgAggregate
            );
            results.add(packageMetrics);
        }
        return results;
    }

    private static class TokenCount {
        Set<String> operators = new HashSet<>();
        Set<String> operands = new HashSet<>();
        int totalOperators;
        int totalOperands;
    }

    private TokenCount gatherOperatorsAndOperands(CtMethod<?> method) {
        TokenCount tokenCount = new TokenCount();

        // ---------------------------
        // OPERANDS
        // ---------------------------

        // 1) 'this' references
        method.getElements(new TypeFilter<>(CtThisAccess.class)).forEach(thisAccess -> {
            tokenCount.operands.add("this");
            tokenCount.totalOperands++;
        });

        // 2) Identifier references (variable reads/writes)
        method.getElements(new TypeFilter<>(CtVariableRead.class)).forEach(variableRef -> {
            tokenCount.operands.add(variableRef.getVariable().getSimpleName());
            tokenCount.totalOperands++;
        });
        method.getElements(new TypeFilter<>(CtVariableWrite.class)).forEach(variableRef -> {
            tokenCount.operands.add(variableRef.getVariable().getSimpleName());
            tokenCount.totalOperands++;
        });

        // 3) Parameter names (optional, if you want them distinctly counted as operands)
        for (var param : method.getParameters()) {
            tokenCount.operands.add(param.getSimpleName());
            tokenCount.totalOperands++;
        }

        // 4) Type names (e.g., String, MyClass, etc.)
        method.getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeRef -> {
            tokenCount.operands.add(typeRef.getSimpleName());
            tokenCount.totalOperands++;
        });

        // 5) Constants / literals (numeric, char, string, etc.)
        method.getElements(new TypeFilter<>(CtLiteral.class)).forEach(literal -> {
            tokenCount.operands.add(String.valueOf(literal.getValue()));
            tokenCount.totalOperands++;
        });

        // ---------------------------
        // OPERATORS
        // ---------------------------

        // a) Type qualifiers / modifiers
        // We'll inspect local variables, parameters, etc. for modifiers like final, static, volatile...
        // Treat each modifier as a distinct operator token
        method.getElements(new TypeFilter<>(CtLocalVariable.class)).stream().flatMap(localVar -> localVar.getModifiers().stream()).forEach(mod -> {
            tokenCount.operators.add(mod.toString().toLowerCase());
            tokenCount.totalOperators++;
        });
        // Similarly, if you want to track parameter modifiers as well
        method.getParameters().stream().flatMap(param -> param.getModifiers().stream()).forEach(mod -> {
            tokenCount.operators.add(mod.toString().toLowerCase());
            tokenCount.totalOperators++;
        });
        // If you want to track method-level qualifiers (e.g., synchronized)
        // you'd do something like:
        method.getModifiers().forEach(mod -> {
            tokenCount.operators.add(mod.toString().toLowerCase());
            tokenCount.totalOperators++;
        });

        // b) Binary operators
        method.getElements(new TypeFilter<>(CtBinaryOperator.class)).forEach(operator -> {
            tokenCount.operators.add(operator.getKind().toString());
            tokenCount.totalOperators++;
        });

        // c) Control structures
        method.getElements(new TypeFilter<>(CtIf.class)).forEach(ifStmt -> {
            tokenCount.operators.add("if(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtFor.class)).forEach(forStmt -> {
            tokenCount.operators.add("for(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtWhile.class)).forEach(whileStmt -> {
            tokenCount.operators.add("while(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtDo.class)).forEach(doStmt -> {
            tokenCount.operators.add("do...while(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtSwitch.class)).forEach(switchStmt -> {
            tokenCount.operators.add("switch(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtTry.class)).forEach(tryStmt -> {
            tokenCount.operators.add("try(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtCatch.class)).forEach(catchStmt -> {
            tokenCount.operators.add("catch(...)");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtCase.class)).forEach(caseStmt -> {
            tokenCount.operators.add("case:");
            tokenCount.totalOperators++;
        });

        // d) Reserved words: break, continue, default, etc.
        method.getElements(new TypeFilter<>(CtBreak.class)).forEach(breakStmt -> {
            tokenCount.operators.add("break");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtContinue.class)).forEach(contStmt -> {
            tokenCount.operators.add("continue");
            tokenCount.totalOperators++;
        });
        method.getElements(new TypeFilter<>(CtAssert.class)).forEach(assertStmt -> {
            tokenCount.operators.add("assert");
            tokenCount.totalOperators++;
        });
        // You could check for default in switch-case statements, but that depends on Spoon's parsing.

        return tokenCount;
    }
}
