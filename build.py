#!/usr/bin/env python3
import argparse
# import xml.etree.ElementTree
from lxml import etree as eTree

# def build(args):
#     version_text = f'{args.version}v{args.date}-{args.time}'
#     parser = xml.etree.ElementTree.XMLParser(target=xml.etree.ElementTree.TreeBuilder(insert_comments=True)) # Python 3.8
#     elementTree = xml.etree.ElementTree.parse('./updates/updates/0.x/compositeArtifacts.xml',parser)
#     root = elementTree.getroot()
#     childrenNode = root.find("children")
#     new_version = xml.etree.ElementTree.SubElement(childrenNode , 'child')
#     new_version.attrib['location'] = f'../../release/{version_text}' # must be str; cannot be an int
#     currentChildrenSize = int(childrenNode.get('size'))
#     # print(currentChildrenSize)
#     childrenNode.set('size',str(currentChildrenSize + 1) )
#     # Write back to file
#     #et.write('file.xml')
#     elementTree.write('./updates/updates/0.x/compositeArtifacts_test.xml', encoding='UTF-8',xml_declaration=True,short_empty_elements=True)

#     # elementTree = xml.etree.ElementTree.parse('./updates/updates/0.x/compositeContent.xml')

def build_lxml(args,filename):
    version_text = f'{args.version}v{args.date}-{args.time}'

    parser = eTree.XMLParser(remove_blank_text=True)
    elementTree = eTree.parse(filename,parser)
    root = elementTree.getroot()
    childrenNode = root.find("children")
    new_version = eTree.SubElement(childrenNode , 'child')
    new_version.attrib['location'] = f'../../releases/{version_text}' # must be str; cannot be an int
    currentChildrenSize = len(childrenNode.getchildren())
    # print(currentChildrenSize)
    childrenNode.set('size',str(currentChildrenSize) )
    # # Write back to file
    write_tree = eTree.ElementTree(root)
    write_tree.write(filename, pretty_print=True, xml_declaration=True, encoding="utf-8")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
                    prog='ManaDesk Build Script',
                    description='modifies xml as part of the build process',
                    epilog='this python3 script modifies the p2 repo xml as part of the build process')
    parser.add_argument('-d', '--date')
    parser.add_argument('-v', '--version')
    parser.add_argument('-t', '--time')
    args = parser.parse_args()

    # build(args)
    build_lxml(args,'./updates/updates/0.x/compositeArtifacts.xml')
    build_lxml(args,'./updates/updates/0.x/compositeContent.xml')
