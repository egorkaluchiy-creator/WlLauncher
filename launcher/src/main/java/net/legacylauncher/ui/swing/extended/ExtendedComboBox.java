package net.legacylauncher.ui.swing.extended;

import net.legacylauncher.ui.LegacyLauncherFrame;
import net.legacylauncher.ui.converter.StringConverter;
import net.legacylauncher.ui.swing.DefaultConverterCellRenderer;
import net.legacylauncher.ui.theme.Theme;
import net.legacylauncher.util.SwingUtil;

import javax.swing.*;
import java.awt.*;

public class ExtendedComboBox<T> extends JComboBox<T> {
    private static final long serialVersionUID = -4509947341182373649L;
    private StringConverter<T> converter;

    public ExtendedComboBox(ListCellRenderer<? super T> renderer) {
        setModel(new DefaultComboBoxModel<>());
        setRenderer(renderer);
        setOpaque(false);
        setFont(getFont().deriveFont(LegacyLauncherFrame.getFontSize()));
        setPreferredSize(new Dimension(0, SwingUtil.magnify(36)));
        if (getEditor() != null && getEditor().getEditorComponent() instanceof JComponent) {
            ((JComponent) getEditor().getEditorComponent()).setOpaque(false);
        }
    }

    public ExtendedComboBox(StringConverter<T> converter) {
        this(new DefaultConverterCellRenderer<>(converter));
        this.converter = converter;
    }

    public ExtendedComboBox() {
        this((ListCellRenderer<T>) null);
    }

    public MutableComboBoxModel<T> getMutableModel() {
        return (MutableComboBoxModel<T>) getModel();
    }

    @SuppressWarnings("unchecked")
    public T getSelectedValue() {
        return (T) getSelectedItem();
    }

    public void setSelectedValue(T value) {
        setSelectedItem(value);
    }

    public void setSelectedValue(String string) {
        T value = convert(string);
        if (value != null) {
            setSelectedValue(value);
        }
    }

    public StringConverter<T> getConverter() {
        return converter;
    }

    public void setConverter(StringConverter<T> converter) {
        this.converter = converter;
    }

    protected String convert(T obj) {
        return converter != null ? converter.toValue(obj) : (obj == null ? null : obj.toString());
    }

    protected T convert(String from) {
        return converter == null ? null : converter.fromString(from);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setOpaque(false);
        if (getEditor() != null && getEditor().getEditorComponent() instanceof JComponent) {
            ((JComponent) getEditor().getEditorComponent()).setOpaque(false);
        }
    }
}
