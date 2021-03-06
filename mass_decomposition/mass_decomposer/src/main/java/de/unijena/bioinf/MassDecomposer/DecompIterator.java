/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.MassDecomposer;

public interface DecompIterator<T> {

    /**
     * moves the iterator one step.
     * @return true, if a new decomposition is found. false, if the iterator reached the end.
     */
    boolean next();

    /**
     * Give access to the current compomere. Please note that this array is only valid during the current iteration step
     * and might be changed afterwards. Furthermore, it is absolutely forbidden to write anything into this array.
     * However, you are free to clone the array and do anything with its copy.
     *
     * @return the compomere (a tuple (a_1,...,a_n) with a_i is the amount of the i-th character in the ordered alphabet
     */
    int[] getCurrentCompomere();

    /**
     * @return the underlying (possibly unordered) alphabet
     */
    Alphabet<T> getAlphabet();

    /**
     * The order of characters in the compomere might be different to the order of characters in the alphabet (i.e.
     * the characters in the compomere are always ordered by mass). This array maps the i-th character in the compomere
     * to it's appropiate index in the alphabet
     * @return mapping of positions in compomere to character indizes in alphabet
     */
    int[] getAlphabetOrder();

    /**
     * Returns the character on the given position in the compomere
     * @param index index in compomere
     * @return corresponding character in alphabet
     */
    T getCharacterAt(int index);

}
