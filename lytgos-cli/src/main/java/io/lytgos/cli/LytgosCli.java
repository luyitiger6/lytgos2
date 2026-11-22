package io.lytgos.cli;

import io.lytgos.core.Lytgos;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/** Picocli entry point skeleton. Subcommands will be added incrementally. */
@CommandLine.Command(
    name = "lytgos",
    mixinStandardHelpOptions = true,
    version = Lytgos.NAME + " " + Lytgos.VERSION,
    description = "LytgOS2 command line interface")
public class LytgosCli implements Callable<Integer> {

  @CommandLine.Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().getOut().println(Lytgos.NAME + " " + Lytgos.VERSION + " CLI skeleton");
    return 0;
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new LytgosCli()).execute(args));
  }
}
