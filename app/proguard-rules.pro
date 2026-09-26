# Project-specific R8 rules. Add keep rules here as libraries that need them
# (JGit, Sora Editor, ...) are introduced in later milestones.

# M6: JGit reaches some config/transport classes reflectively.
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
