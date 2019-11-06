package de.unijena.bioinf.projectspace.sirius;

import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.projectspace.*;

import java.io.IOException;
import java.util.regex.Pattern;

import static de.unijena.bioinf.projectspace.sirius.SiriusLocations.TREES;

public class CompoundContainerSerializer implements ContainerSerializer<CompoundContainerId, CompoundContainer> {

    @Override
    public void writeToProjectSpace(ProjectWriter writer, ProjectWriter.ForContainer containerSerializer, CompoundContainerId id, CompoundContainer container) throws IOException {
        // ensure that we are in the right directory
        writer.inDirectory(id.getDirectoryName(), ()->{
            containerSerializer.writeAllComponents(writer, container, container::getAnnotation);
            return true;
        });
    }

    private final static Pattern resultPattern = Pattern.compile("(\\d+)_([^_]+)_(.+)\\.json");

    @Override
    public CompoundContainer readFromProjectSpace(ProjectReader reader, ProjectReader.ForContainer<CompoundContainerId, CompoundContainer> containerSerializer, CompoundContainerId id) throws IOException {
        return reader.inDirectory(id.getDirectoryName(), ()->{
            final CompoundContainer container = new CompoundContainer(id);
            if (reader.exists(TREES.relDir())) {
                reader.inDirectory(TREES.relDir(), () -> {
                    for (String file : reader.list("*" + TREES.fileExtDot())) { //todo change to score
                        final String name = file.substring(0, file.length() - TREES.fileExtDot().length());
                        String[] pt = name.split("_");
                        final FormulaResultId fid = new FormulaResultId(id, MolecularFormula.parseOrThrow(pt[0]), PrecursorIonType.fromString(pt[1]));
                        container.results.put(fid.fileName(), fid);
                    }
                    return true;
                });
            }

            containerSerializer.readAllComponents(reader, container, container::setAnnotation);
            return container;
        });
    }

    @Override
    public void deleteFromProjectSpace(ProjectWriter writer, ProjectWriter.DeleteContainer<CompoundContainerId> containerSerializer, CompoundContainerId id) throws IOException {
        writer.inDirectory(id.getDirectoryName(), ()->{
            containerSerializer.deleteAllComponents(writer, id);
            return null;
        });
        writer.delete(id.getDirectoryName());
    }
}
